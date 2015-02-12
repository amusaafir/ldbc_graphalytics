import sys
import os

import graphlab as gl
from graphlab.deploy.environment import Hadoop
import time


__author__ = 'Jorai Rijsdijk'


def create_environment(hadoop_home, memory_mb, virtual_cores):
    """
    Create a (distributed) Hadoop environment with the given hadoop_home, memory_mb and virtual_cores

    :param hadoop_home: The location of the hadoop_home to get the hadoop config files from hadoop_home/etc/hadoop
    :param memory_mb: The amount of memory to use for processing the algorithm
    :param virtual_cores: The amount of virtual cores to use for graph processing
    :return: The created Hadoop environment object
    """
    return gl.deploy.environment.Hadoop('Hadoop', config_dir=hadoop_home + '/etc/hadoop', memory_mb=memory_mb,
                                        virtual_cores=virtual_cores, gl_source=None)


if len(sys.argv) < 5:
    exit(1)
    print >> sys.stderr, "Too few arguments, need at least 4: <use_hadoop> [virtual_cores] [heap_size] <graph_file> <directed> <edge_based> [save_result_graph]"

# Read arguments
use_hadoop = sys.argv[1] == "true"
save_result_graph = False

if use_hadoop:
    if len(sys.argv) < 7:
        print >> sys.stderr, "Too few arguments for use_hadoop=true, need at least 6: <use_hadoop=true> <virtual_cores> <heap_size> <graph_file> <directed> <edge_based> [save_result_graph]"
        exit(1)
    else:
        virtual_cores = sys.argv[2]
        heap_size = sys.argv[3]
        graph_file = sys.argv[4]
        directed = sys.argv[5] == "true"
        edge_based = sys.argv[6] == "true"
        # Create hadoop environment object
        hadoop_home = os.environ.get('HADOOP_HOME')
        hadoop = create_environment(hadoop_home=hadoop_home, memory_mb=heap_size, virtual_cores=virtual_cores)
        if len(sys.argv) >= 8:
            save_result_graph = sys.argv[7] == "true"
else:
    graph_file = sys.argv[2]
    directed = sys.argv[3] == "true"
    edge_based = sys.argv[4] == "true"
    if len(sys.argv) >= 6:
        save_result_graph = sys.argv[5] == "true"

if not edge_based:
    print >> sys.stderr, "Vertex based graph format not supported yet"
    exit(2)


def load_graph_task(task):
    import graphlab as gl_

    graph_data = gl_.SFrame.read_csv(task.params['csv'], header=False, delimiter=' ', column_type_hints=long)
    task.outputs['graph'] = gl.SGraph().add_edges(graph_data, src_field='X1', dst_field='X2')


def connected_components_model(task):
    import graphlab as gl_

    graph = task.inputs['data']
    task.outputs['cc_graph'] = gl_.connected_components.create(graph)

if use_hadoop:  # Deployed execution
    # Define the graph loading task
    load_graph = gl.deploy.Task('load_graph')
    load_graph.set_params({'csv': graph_file})
    load_graph.set_code(load_graph_task)
    load_graph.set_outputs(['graph'])

    # Define the shortest_path model create task
    connected = gl.deploy.Task('connected_components')
    connected.set_inputs({'data': ('load_graph', 'graph')})
    connected.set_code(connected_components_model)
    connected.set_outputs(['cc_graph'])

    # Create the job and deploy it to the Hadoop cluster
    hadoop_job = gl.deploy.job.create(['load_graph', 'connected_components'], environment=hadoop)
    while hadoop_job.get_status() in ['Pending', 'Running']:
        time.sleep(2)  # sleep for 2s while polling for job to be completed.

    output_graph = connected.outputs['cc_graph'].get('graph')
else:  # Local execution
    # Stub task class
    class Task:
        def __init__(self, **keywords):
            self.__dict__.update(keywords)

    # Stub task object to keep function definitions intact
    cur_task = Task(params={'csv': graph_file}, inputs={}, outputs={})

    load_graph_task(cur_task)
    cur_task.inputs['data'] = cur_task.outputs['graph']
    connected_components_model(cur_task)
    output_graph = cur_task.outputs['cc_graph'].get('graph')

if save_result_graph:
    output_graph.save('target/conn_%s' % (graph_file[graph_file.rfind('/', 0, len(graph_file) - 2) + 1:]))