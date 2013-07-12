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

import ivory.core.Constants;
import ivory.core.RetrievalEnvironment;
import ivory.core.data.document.IntDocVector;
import ivory.core.data.document.WeightedIntDocVector;
import ivory.core.data.index.PostingsList;
import ivory.core.data.index.PostingsListDocSortedPositional;
import ivory.core.util.RandomizedDocNos;
import ivory.lsh.driver.PwsimEnvironment;

import java.io.File;
import java.io.IOException;
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
import org.apache.hadoop.mapred.Reducer;
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
public class KmeansGetInitialCentroids extends PowerTool {
  private static final Logger sLogger = Logger.getLogger(KmeansGetInitialCentroids.class);

  static{
    sLogger.setLevel(Level.INFO);
  }
  protected static enum Docs{
    Total
  }
  protected static enum Terms{
    OOV, NEG
  }

//  private static class MyReducer extends MapReduceBase implements
//    Reducer<IntWritable,WeightedIntDocVector>{
//    
//  }

  
  private static class MyMapper extends MapReduceBase implements
      Mapper<IntWritable, WeightedIntDocVector, IntWritable, WeightedIntDocVector> {

    static IntWritable mDocno = new IntWritable();
    private Vocab engVocabH;
    private ArrayListWritable<IntWritable> centroidDocNos;
    private String initialDocNoPath;
    private Path docnoPath;
    private FileSystem fs; 
    private ArrayList<IntWritable> initialCentroidDocs;
    ArrayListWritable<IntWritable>  docnos;
    RandomizedDocNos docnorand;
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
     if(p.toString().contains("kmeans_randomdocs")){
            sLogger.info("It contained it!!!!");
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
      
      docnorand = new RandomizedDocNos(conf,localDir);
      initialCentroidDocs = new ArrayList<IntWritable>();
      try {
        docnorand.readRandomDocs(initialCentroidDocs);
      } catch (IOException e) {
        sLogger.info("Failed to read in initial centroids in configure");
        e.printStackTrace();
      }
      
      initialDocNoPath = conf.get("InitialDocnoPath");
      }

//    THESE LINES SHOW ORIGINAL VERSION
//    public void map (IntWritable docno, WeightedIntDocVector doc,
//        OutputCollector<IntWritable, WeightedIntDocVector> output, Reporter reporter)
    
    public void map (IntWritable docno, WeightedIntDocVector doc,
        OutputCollector<IntWritable, WeightedIntDocVector> output, Reporter reporter)
    throws IOException {	
      mDocno.set(docno.get());
      if(initialCentroidDocs.contains(mDocno)){
        sLogger.info("mDocno:" + mDocno.get() + " was contained");
        output.collect(mDocno, doc);
      }else{
        ;
      }
      reporter.incrCounter(Docs.Total, 1);
    }
  }

  public static final String[] RequiredParameters = { "Ivory.NumMapTasks",
    "Ivory.IndexPath", 
  };

  public String[] getRequiredParameters() {
    return RequiredParameters;
  }

  public KmeansGetInitialCentroids(Configuration conf) {
    super(conf);
  }

  @SuppressWarnings("deprecation")
  public int runTool() throws Exception {
    sLogger.info("PowerTool: " + KmeansGetInitialCentroids.class.getName());

    JobConf conf = new JobConf(getConf(), KmeansGetInitialCentroids.class);
    FileSystem fs = FileSystem.get(conf);
    

    String indexPath = getConf().get(Constants.IndexPath);

    RetrievalEnvironment env = new RetrievalEnvironment(indexPath, fs);

    String outputPath = env.getKmeansCentroidDirectory();
//    String docnoDir = env.getInitialDocnoDirectory(); //I think I need to change this to the weighted int docvecs
    int mapTasks = getConf().getInt(Constants.NumMapTasks, 0);
    int minSplitSize = getConf().getInt(Constants.MinSplitSize, 0);
    String collectionName = getConf().get(Constants.CollectionName);

    String documentType = conf.get(Constants.KmeansDocumentType,
        IntDocVector.class.getCanonicalName());
    @SuppressWarnings("unchecked")
    Class<? extends IntDocVector> documentClass =
        (Class<? extends IntDocVector>) Class.forName(documentType);
     
    
    
    sLogger.info("Characteristics of the collection:");
    sLogger.info(" - CollectionName: " + collectionName);
    sLogger.info("Characteristics of the job:");
    sLogger.info(" - NumMapTasks: " + mapTasks);
    sLogger.info(" - MinSplitSize: " + minSplitSize);

    DistributedCache.addCacheFile(new Path(env.getKmeansRandomDocNoPath()).toUri(), conf);

   
//    Path inputPath = new Path(PwsimEnvironment.getTermDocvectorsFile(indexPath, fs));
//    Path inputPath = new Path(env.getIntDocVectorsDirectory());
    Path inputPath = new Path(PwsimEnvironment.getIntDocvectorsFile(indexPath, fs));
    Path kMeansPath = new Path(outputPath);

    if (fs.exists(kMeansPath)) {
      sLogger.info("Output path already exists!");
      return -1;
    }
    
//    initialCentroidDocs = new ArrayListWritable<IntWritable>();
    
    
    
    
    conf.setJobName(KmeansGetInitialCentroids.class.getSimpleName() + ":" + collectionName);
    conf.setNumMapTasks(mapTasks);
    conf.setNumReduceTasks(0);
    conf.setInt("mapred.min.split.size", minSplitSize);
    conf.set("mapred.child.java.opts", "-Xmx2048m");
    FileInputFormat.setInputPaths(conf, inputPath);
    FileOutputFormat.setOutputPath(conf, kMeansPath);

    conf.setInputFormat(SequenceFileInputFormat.class);
    conf.setMapOutputKeyClass(IntWritable.class);
    conf.setMapOutputValueClass(documentClass);
    conf.setOutputFormat(SequenceFileOutputFormat.class);
    conf.setOutputKeyClass(IntWritable.class);
    conf.setOutputValueClass(documentClass);

    conf.setMapperClass(MyMapper.class);

    long startTime = System.currentTimeMillis();

    RunningJob rj = JobClient.runJob(conf);
    sLogger.info("Job Finished in " + (System.currentTimeMillis() - startTime) / 1000.0
        + " seconds");
    Counters counters = rj.getCounters();

    env.writeKmeansType(documentType);

    return (int) getConf().getInt(Constants.KmeansClusterCount, 5);
  }
}
