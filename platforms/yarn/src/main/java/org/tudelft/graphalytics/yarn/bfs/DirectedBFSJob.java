package org.tudelft.graphalytics.yarn.bfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.*;
import org.apache.hadoop.mapred.jobcontrol.Job;
import org.apache.hadoop.util.Tool;

import java.io.IOException;

public class DirectedBFSJob extends Configured implements Tool {
    // Stopping condition
    public enum Node {
        VISITED
    }

    public int run(String[] args) throws IOException {
        boolean isFinished = false;
        int iteration = 0;
        long counter = 0;
        JobConf initBFSConf = new JobConf(new Configuration());

        long t0 = System.currentTimeMillis();

        while (!isFinished) {
            Configuration config = new Configuration();
            config.set("mapreduce.jobtracker.address", "localhost:50030");
            config.set("fs.defaultFS", "hdfs://localhost:9000");
            
            initBFSConf = new JobConf(config);
            Job job = new Job(initBFSConf);
            RunningJob runningJob;

            initBFSConf.setJarByClass(DirectedBFSJob.class);

            initBFSConf.setMapOutputKeyClass(IntWritable.class);
            initBFSConf.setMapOutputValueClass(Text.class);

            initBFSConf.setMapperClass(DirectedBFSMap.class);
            initBFSConf.setReducerClass(GenericBFSReducer.class);

            initBFSConf.setOutputKeyClass(NullWritable.class);
            initBFSConf.setOutputValueClass(Text.class);

            initBFSConf.setInputFormat(TextInputFormat.class);
            initBFSConf.setOutputFormat(TextOutputFormat.class);

            initBFSConf.setNumMapTasks(Integer.parseInt(args[2]));
            initBFSConf.setNumReduceTasks(Integer.parseInt(args[3]));

            // src node ID
            initBFSConf.set(BFSJob.SRC_ID_KEY, BFSJob.SRC_ID);

            //platform config
            /* Comment to perform test in local-mode

            /* pseudo */
            /*initBFSConf.set("io.sort.mb", "768");
            initBFSConf.set("fs.inmemory.size.mb", "768");
            initBFSConf.set("io.sort.factor", "50");*/

            /* DAS4 conf Hadoop ver 0.20.203 */
            //initBFSConf.set("io.sort.mb", "1536");
            //initBFSConf.set("io.sort.factor", "80");
            //initBFSConf.set("fs.inmemory.size.mb", "1536");

            FileSystem dfs = FileSystem.get(config);

            if(iteration % 2 == 0) {
                dfs = FileSystem.get(config);
                dfs.delete(new Path(args[5]+"/BFS"), true);

                FileInputFormat.addInputPath(initBFSConf, new Path(args[5] + "/nodeGraph"));
                FileOutputFormat.setOutputPath(initBFSConf, new Path(args[5]+"/BFS"));
                runningJob = JobClient.runJob(initBFSConf);
            } else {
                dfs = FileSystem.get(config);
                dfs.delete(new Path(args[5]+"/nodeGraph"), true);

                FileInputFormat.addInputPath(initBFSConf, new Path(args[5]+"/BFS"));
                FileOutputFormat.setOutputPath(initBFSConf, new Path(args[5]+"/nodeGraph"));
                runningJob = JobClient.runJob(initBFSConf);
            }

            Counters counters = runningJob.getCounters();
            counter = counters.getCounter(DirectedBFSJob.Node.VISITED);

            System.out.println("\n************************************");
            System.out.println("* BFS Iteration "+(iteration+1)+" FINISHED *");
            System.out.println("************************************\n");

            if(counter == 0)
                isFinished = true;

            iteration++;
        }

        // recording end time of stats
        long t1 = System.currentTimeMillis();

        double elapsedTimeSeconds = (t1 - t0)/1000.0;
        System.out.println("Stats_Texe = "+elapsedTimeSeconds);

        /*
            Clean UP

         */
        Configuration config = new Configuration();
        FileSystem dfs = FileSystem.get(config);

        // by the end of iteration iterationCounter is increased, thus del opposite directories then within WHILE()
        if(iteration % 2 == 0) {
            dfs = FileSystem.get(config);
            dfs.delete(new Path(args[5]+"/BFS"), true);
        } else {
            dfs = FileSystem.get(config);
            dfs.delete(new Path(args[5]+"/nodeGraph"), true);
        }

        // creating benchmark data
        try{
            FileSystem fs = FileSystem.get(initBFSConf);
            Path path = new Path(args[5]+"/benchmark.txt");
            FSDataOutputStream os = fs.create(path);
            String benchmarkData = "elapsed time: "+elapsedTimeSeconds+"\nsteps: "+iteration;
            os.write(benchmarkData.getBytes());
            os.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return 0;
    }
}

