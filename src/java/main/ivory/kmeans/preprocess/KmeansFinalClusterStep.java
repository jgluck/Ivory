/*
 * Ivory: A Hadoop toolkit for web-scale information retrieval
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package ivory.kmeans.preprocess;

import ivory.core.Constants;
import ivory.core.RetrievalEnvironment;
import ivory.core.data.document.DocnoWeightedIntDocVectorPair;
import ivory.core.data.document.WeightedIntDocVector;
import ivory.core.preprocess.BuildWeightedIntDocVectors;
import ivory.core.util.CLIRUtils;
import ivory.kmeans.util.KmeansUtility;
import ivory.lsh.driver.PwsimEnvironment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapred.Counters;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mortbay.log.Log;

import edu.umd.cloud9.example.memcached.demo.WordCount.MyReducer;
import edu.umd.cloud9.io.array.ArrayListWritable;
import edu.umd.cloud9.io.map.HMapIFW;
import edu.umd.cloud9.io.map.HMapSFW;
import edu.umd.cloud9.util.PowerTool;
import edu.umd.cloud9.util.map.MapKF;
import edu.umd.hooka.Vocab;
import edu.umd.hooka.alignment.HadoopAlign;

/**
 * This time we don't want to update the centroids. We find cluster membership for each document.
 * Then we output them. with membership information.
 * @author jgluck
 *
 */
public class KmeansFinalClusterStep extends PowerTool {
  private static final Logger sLogger = Logger.getLogger(BuildWeightedIntDocVectors.class);

  static{
    sLogger.setLevel(Level.INFO);
  }
  protected static enum Docs{
    Total
  }
  protected static enum Terms{
    OOV, NEG
  }
  
  /**
   * Outputs Key: Clustermembership Value: Documentnumber-Document pair
   * @author jgluck
   *
   */
  private static class MyReducer extends MapReduceBase implements
  Reducer<IntWritable, DocnoWeightedIntDocVectorPair, IntWritable, IntWritable> {
    private String initialDocNoPath;
    private FileSystem fs; 
    Path targetDir;
    
    public void configure(JobConf conf){
      try {
        fs = FileSystem.get(conf);
      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
        throw new RuntimeException("Error getting the filesystem conf");
      }
      
    }
    
    public void reduce(IntWritable clusterMembership, Iterator<DocnoWeightedIntDocVectorPair> vectors,
        OutputCollector<IntWritable, IntWritable> output, Reporter reporter) throws IOException {
      Log.info("Adding vectors with cluster membership: " + clusterMembership);
      while(vectors.hasNext()){
        output.collect(clusterMembership, vectors.next().getDocno());
      }
    }
  }
    
 /**
  * Works almost the same as the mapper in cluster on centroids but includes the
  * document number of the output.
  * @author jgluck
  *
  */
  private static class MyMapper extends MapReduceBase implements
      Mapper<IntWritable, WeightedIntDocVector, IntWritable, DocnoWeightedIntDocVectorPair> {

    static IntWritable mDocno = new IntWritable();
    private boolean normalize = false;
    private ArrayListWritable<IntWritable> centroidDocNos;
    private String initialDocNoPath;
    private Path docnoPath;
    private FileSystem fs; 
    private ArrayList<WeightedIntDocVector> initialCentroids;
    ArrayListWritable<IntWritable>  docnos;
    KmeansUtility docnorand;
    Path[] localFiles;
    Path localDir;

    public void configure(JobConf conf){
      try {
       localFiles = DistributedCache.getLocalCacheFiles(conf);
     } catch (IOException e2) {
       // TODO Auto-generated catch block
       sLogger.info("Failed to get local cache files");
       e2.printStackTrace();
     }
      
      sLogger.info("Local Files: :"+  localFiles);
      for (Path p : localFiles) {
      if(p.toString().contains("kmeans_centroid")){
             sLogger.info("Found the Kmeans_CentroidFile");
             localDir = p;
          }
      }
      
      
       try {
         fs = FileSystem.get(conf);
       } catch (IOException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
         throw new RuntimeException("Error getting the filesystem conf");
       }
       
       docnorand = new KmeansUtility(conf,localDir);
       initialCentroids = new ArrayList<WeightedIntDocVector>();
       try {
         docnorand.readCurrentCentroids(initialCentroids);
       } catch (IOException e) {
         sLogger.info("Failed to read in initial centroids in configure");
         e.printStackTrace();
       }
       
       normalize = conf.getBoolean("Ivory.Normalize", false);
       initialDocNoPath = conf.get("InitialDocnoPath");
      }

    
    public void map (IntWritable docno, WeightedIntDocVector doc,
        OutputCollector<IntWritable, DocnoWeightedIntDocVectorPair> output, Reporter reporter)
    throws IOException {	
      float max = -500;
      float similarity = 0;
      IntWritable cluster = new IntWritable(0);
      int it = 0;
      DocnoWeightedIntDocVectorPair toOutput = new DocnoWeightedIntDocVectorPair(docno,doc);
      for(WeightedIntDocVector centroid: initialCentroids){
        similarity = CLIRUtils.cosine(doc, centroid);
        if(similarity>max){
          max = similarity;
          cluster.set(it);
        }
        it= it +1;
      }
      output.collect(cluster, toOutput); //second mdocno should be what cluster we want
    }
  }

  public static final String[] RequiredParameters = { "Ivory.NumMapTasks",
    "Ivory.IndexPath", 
    "Ivory.Normalize",
  };

  public String[] getRequiredParameters() {
    return RequiredParameters;
  }

  public KmeansFinalClusterStep(Configuration conf) {
    super(conf);
  }

  public int runTool() throws Exception {
    sLogger.info("PowerTool: " + KmeansFinalClusterStep.class.getName());

    JobConf conf = new JobConf(getConf(), KmeansFinalClusterStep.class);
    FileSystem fs = FileSystem.get(conf);

    String indexPath = getConf().get("Ivory.IndexPath");

    RetrievalEnvironment env = new RetrievalEnvironment(indexPath, fs);

    
    String centroidPath = env.getKmeansCentroidDirectory();
    String outputPath = env.getKmeansFinalDirectory();
    int mapTasks = getConf().getInt("Ivory.NumMapTasks", 0);
    int minSplitSize = getConf().getInt("Ivory.MinSplitSize", 0);
    String collectionName = getConf().get("Ivory.CollectionName");

    
    
    
    sLogger.info("Characteristics of the collection:");
    sLogger.info(" - CollectionName: " + collectionName);
    sLogger.info("Characteristics of the job:");
    sLogger.info(" - NumMapTasks: " + mapTasks);
    sLogger.info(" - MinSplitSize: " + minSplitSize);


    Integer numClusters = getConf().getInt(Constants.KmeansClusterCount, 5);
   
    DistributedCache.addCacheFile(new Path(env.getCurrentCentroidPath()).toUri(), conf);

    
    Path inputPath = new Path(PwsimEnvironment.getIntDocvectorsFile(indexPath, fs));

    Path kMeansPath = new Path(outputPath);

    if (fs.exists(kMeansPath)) {
      sLogger.info("Output path already exists!");
      return -1;
    }
    
    
    conf.setJobName(KmeansFinalClusterStep.class.getSimpleName() + ":" + collectionName);
    conf.setNumMapTasks(mapTasks);
    conf.setNumReduceTasks(100); //TODO FIX NUM REDUCERS
    conf.setInt("mapred.min.split.size", minSplitSize);
    conf.set("mapred.child.java.opts", "-Xmx2048m");
    conf.setBoolean("Ivory.Normalize", getConf().getBoolean("Ivory.Normalize", true));
    FileInputFormat.setInputPaths(conf, inputPath);
    FileOutputFormat.setOutputPath(conf, kMeansPath);

    conf.setInputFormat(SequenceFileInputFormat.class);
    conf.setMapOutputKeyClass(IntWritable.class);
    conf.setMapOutputValueClass(DocnoWeightedIntDocVectorPair.class);
    conf.setOutputFormat(SequenceFileOutputFormat.class);
    conf.setOutputKeyClass(IntWritable.class);
    conf.setOutputValueClass(IntWritable.class);
   

    conf.setMapperClass(MyMapper.class);
    conf.setReducerClass(MyReducer.class);

    long startTime = System.currentTimeMillis();

    RunningJob rj = JobClient.runJob(conf);
    sLogger.info("Job Finished in " + (System.currentTimeMillis() - startTime) / 1000.0
        + " seconds");
    Counters counters = rj.getCounters();

    long numOfDocs= (long) counters.findCounter(Docs.Total).getCounter();
    conf.setInt("CurrentRun", conf.getInt("CurrentRun",0)+1);

    return (int) numOfDocs;
  }
}