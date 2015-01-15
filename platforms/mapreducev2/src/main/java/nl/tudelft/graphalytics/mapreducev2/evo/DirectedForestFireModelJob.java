package nl.tudelft.graphalytics.mapreducev2.evo;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.*;
import nl.tudelft.graphalytics.algorithms.EVOParameters;
import nl.tudelft.graphalytics.mapreducev2.MapReduceJob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Job specification for forest fire model on MapReduce version 2 for directed graphs.
 *
 * @author Tim Hegeman
 */
public class DirectedForestFireModelJob extends MapReduceJob<EVOParameters> {
	
	private Map<LongWritable, List<LongWritable>> burnedEdges;
	
    public DirectedForestFireModelJob(String inputPath, String intermediatePath, String outputPath, EVOParameters parameters) {
    	super(inputPath, intermediatePath, outputPath, parameters);
    	burnedEdges = new HashMap<>();
    }
	
    @Override
	protected Class<?> getMapOutputKeyClass() {
		return LongWritable.class;
	}

	@Override
	protected Class<?> getMapOutputValueClass() {
		return Text.class;
	}

	@Override
	protected Class<?> getOutputKeyClass() {
		return NullWritable.class;
	}

	@Override
	protected Class<?> getOutputValueClass() {
		return Text.class;
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected Class<? extends InputFormat> getInputFormatClass() {
		return TextInputFormat.class;
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	protected Class<? extends OutputFormat> getOutputFormatClass() {
		return TextOutputFormat.class;
	}
	
	@SuppressWarnings("rawtypes")
	@Override
	protected Class<? extends Mapper> getMapperClass() {
		return DirectedForestFireModelMap.class;
	}

	@SuppressWarnings("rawtypes")
	@Override
	protected Class<? extends Reducer> getReducerClass() {
		return DirectedForestFireModelReducer.class;
	}

	@Override
	protected boolean isFinished() {
		return (getIteration() >= getParameters().getMaxIterations() ||
				(getIteration() > 0 && burnedEdges.isEmpty()));
	}

	@Override
	protected void setConfigurationParameters(JobConf jobConfiguration) {
		super.setConfigurationParameters(jobConfiguration);
		jobConfiguration.setLong(ForestFireModelUtils.MAX_ID, getParameters().getMaxId() + 1);
    	jobConfiguration.setFloat(ForestFireModelUtils.P_RATIO, getParameters().getPRatio());
    	jobConfiguration.setFloat(ForestFireModelUtils.R_RATIO, getParameters().getRRatio());
    	jobConfiguration.set(ForestFireModelUtils.CURRENT_AMBASSADORS, ForestFireModelUtils.verticesIDsMap2String(burnedEdges));
    	
    	if (getIteration() == 1) {
    		if (getNumMappers() > 0) {
    			jobConfiguration.setInt(ForestFireModelUtils.NEW_VERTICES_NR, getParameters().getNumNewVertices() / getNumMappers());
    			jobConfiguration.setInt(ForestFireModelUtils.ID_SHIFT, getNumMappers());
    		} else {
    			jobConfiguration.setInt(ForestFireModelUtils.NEW_VERTICES_NR, getParameters().getNumNewVertices());
    			jobConfiguration.setInt(ForestFireModelUtils.ID_SHIFT, 1024 * 1024);
    		}
    		jobConfiguration.setBoolean(ForestFireModelUtils.IS_INIT, true);
    	}
	}

	@Override
	protected void processJobOutput(RunningJob jobExecution) throws IOException {
		Counters counters = jobExecution.getCounters();
        Counters.Group burned = counters.getGroup(ForestFireModelUtils.NEW_VERTICES);
        burnedEdges.clear(); // clean previous iteration data
        for(Counters.Counter counter : burned) {
            String data[] = counter.getName().split(",");
            String newVertex = data[0];
            LongWritable newVertexLong = new LongWritable(Long.parseLong(newVertex));
            String ambassador = data[1];
            LongWritable ambassadorLong = new LongWritable(Long.parseLong(ambassador));

            List<LongWritable> ambassadors = (burnedEdges.containsKey(newVertexLong) ?
            		burnedEdges.get(newVertexLong) : new ArrayList<LongWritable>());
            ambassadors.add(ambassadorLong);
            burnedEdges.put(newVertexLong, ambassadors);
        }

        System.out.println("\n************************************");
        System.out.println("* FFM Hoops " + getIteration() + " FINISHED *");
        System.out.println("************************************\n");
	}
	
}