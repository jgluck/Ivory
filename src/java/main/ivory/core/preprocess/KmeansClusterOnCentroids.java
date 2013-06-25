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

package ivory.core.preprocess;

import ivory.core.RetrievalEnvironment;
import ivory.core.data.document.WeightedIntDocVector;
import ivory.lsh.driver.PwsimEnvironment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

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
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import edu.umd.cloud9.io.array.ArrayListWritable;
import edu.umd.cloud9.io.map.HMapIFW;
import edu.umd.cloud9.io.map.HMapSFW;
import edu.umd.cloud9.util.PowerTool;
import edu.umd.cloud9.util.map.MapKF;
import edu.umd.hooka.Vocab;
import edu.umd.hooka.alignment.HadoopAlign;

/**
 * Map term doc vectors into int doc vectors using the term-to-id mapping. 
 * This task is the same in either cross-lingual or mono-lingual case. That is, this task works for the case where doc vectors are translated into English and the case where doc vectors are originally in English.
 * Also, weights in doc vector are normalized.
 * 
 * @author ferhanture
 *
 */
public class KmeansClusterOnCentroids extends PowerTool {
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

  private static class MyMapper extends MapReduceBase implements
      Mapper<IntWritable, WeightedIntDocVector, IntWritable, IntWritable> {

    static IntWritable mDocno = new IntWritable();
    private boolean normalize = false;
    private Vocab engVocabH;
    private ArrayListWritable<IntWritable> centroidDocNos;
    private String initialDocNoPath;
    private Path docnoPath;
    private FileSystem fs; 
    ArrayListWritable<IntWritable>  docnos;
    Path[] localFiles;
    

    public void configure(JobConf conf){
     
      try {
        fs = FileSystem.get(conf);
      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
        throw new RuntimeException("Error getting the filesystem conf");
      }
      
      normalize = conf.getBoolean("Ivory.Normalize", false);
      initialDocNoPath = conf.get("InitialDocnoPath");
      docnoPath = new Path(initialDocNoPath);
      FSDataInputStream in;
      try {
        localFiles = DistributedCache.getLocalCacheFiles(conf);
      } catch (IOException e2) {
        // TODO Auto-generated catch block
        e2.printStackTrace();
      }
      try {
        in = fs.open(docnoPath);
      } catch (IOException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
        throw new RuntimeException("Error opening the docnopath!");
      }
      docnos = new ArrayListWritable<IntWritable>();
      
      try{
        docnos.readFields(in);
      }catch (Exception e){
        e.printStackTrace();
        throw new RuntimeException("Error getting initial docnos back from hdfs!");
      }
      }

    
    public void map (IntWritable docno, WeightedIntDocVector doc,
        OutputCollector<IntWritable, IntWritable> output, Reporter reporter)
    throws IOException {	
      mDocno.set(docno.get());
      if(docnos.contains(mDocno)){
        output.collect(mDocno, mDocno); //second mdocno should be what cluster we want
      }else{
        ;
      }
      reporter.incrCounter(Docs.Total, 1);
    }
  }

  public static final String[] RequiredParameters = { "Ivory.NumMapTasks",
    "Ivory.IndexPath", 
    "Ivory.Normalize",
  };

  public String[] getRequiredParameters() {
    return RequiredParameters;
  }

  public KmeansClusterOnCentroids(Configuration conf) {
    super(conf);
  }

  public int runTool() throws Exception {
    sLogger.info("PowerTool: " + KmeansClusterOnCentroids.class.getName());

    JobConf conf = new JobConf(getConf(), KmeansClusterOnCentroids.class);
    FileSystem fs = FileSystem.get(conf);

    String indexPath = getConf().get("Ivory.IndexPath");

    RetrievalEnvironment env = new RetrievalEnvironment(indexPath, fs);

    
    String centroidPath = env.getKmeansCentroidDirectory();
    String outputPath = env.getKmeansVectorDirectory();
    String docnoDir = env.getInitialDocnoDirectory(); //I think I need to change this to the weighted int docvecs
    int mapTasks = getConf().getInt("Ivory.NumMapTasks", 0);
    int minSplitSize = getConf().getInt("Ivory.MinSplitSize", 0);
    String collectionName = getConf().get("Ivory.CollectionName");

    
    
    
    sLogger.info("Characteristics of the collection:");
    sLogger.info(" - CollectionName: " + collectionName);
    sLogger.info("Characteristics of the job:");
    sLogger.info(" - NumMapTasks: " + mapTasks);
    sLogger.info(" - MinSplitSize: " + minSplitSize);

    BufferedReader br=new BufferedReader(new InputStreamReader(fs.open(new Path(centroidPath))));
    String line;
    line=br.readLine();
    while (line != null){
            System.out.println(line);
            line=br.readLine();

    }
    if(1==1){
      return -1;
    }
    String vocabFile = getConf().get("Ivory.FinalVocab");
    DistributedCache.addCacheFile(new URI(vocabFile), conf);
    Integer numClusters = getConf().getInt("Ivory.KmeansClusterCount", 5);
   
    ArrayListWritable<IntWritable> initialCentroidDocs = new ArrayListWritable<IntWritable>();

    int numDocs = env.readCollectionDocumentCount();
    for(int i=0;i<numClusters;i++){
      IntWritable randomNumber = new IntWritable(1 + (int)(Math.random()*numDocs));
      while(initialCentroidDocs.contains(randomNumber)){
        randomNumber.set(1 + (int)(Math.random()*numDocs));
      }
      initialCentroidDocs.add(randomNumber);
    }
    
    conf.set("InitialDocnoPath", docnoDir);
    
    
    
    
    Path docnoPath = new Path(docnoDir);
    if(fs.exists(docnoPath)){
      sLogger.info("DocnoDir already exists!");
      return -1;
    }
    FSDataOutputStream out = fs.create(docnoPath);
    
    initialCentroidDocs.write(out);
    
//    Path inputPath = new Path(PwsimEnvironment.getTermDocvectorsFile(indexPath, fs));
    Path inputPath = new Path(PwsimEnvironment.getIntDocvectorsFile(indexPath, fs));
    //we really need to choose numClusters docids and get theri vectors sideloaded in
    Path kMeansPath = new Path(outputPath);

    if (fs.exists(kMeansPath)) {
      sLogger.info("Output path already exists!");
      return -1;
    }
    
    
    conf.setJobName(KmeansClusterOnCentroids.class.getSimpleName() + ":" + collectionName);
    conf.setNumMapTasks(mapTasks);
    conf.setNumReduceTasks(0);
    conf.setInt("mapred.min.split.size", minSplitSize);
    conf.set("mapred.child.java.opts", "-Xmx2048m");
    conf.setBoolean("Ivory.Normalize", getConf().getBoolean("Ivory.Normalize", true));
    FileInputFormat.setInputPaths(conf, inputPath);
    FileOutputFormat.setOutputPath(conf, kMeansPath);

    conf.setInputFormat(SequenceFileInputFormat.class);
    conf.setMapOutputKeyClass(IntWritable.class);
    conf.setMapOutputValueClass(WeightedIntDocVector.class);
    conf.setOutputFormat(SequenceFileOutputFormat.class);
    conf.setOutputKeyClass(IntWritable.class);
    conf.setOutputValueClass(WeightedIntDocVector.class);

    conf.setMapperClass(MyMapper.class);

    long startTime = System.currentTimeMillis();

    RunningJob rj = JobClient.runJob(conf);
    sLogger.info("Job Finished in " + (System.currentTimeMillis() - startTime) / 1000.0
        + " seconds");
    Counters counters = rj.getCounters();

    long numOfDocs= (long) counters.findCounter(Docs.Total).getCounter();

    return (int) numOfDocs;
  }
}