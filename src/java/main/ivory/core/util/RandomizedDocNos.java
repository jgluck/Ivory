package ivory.core.util;

import java.io.IOException;
import java.util.ArrayList;

import ivory.core.RetrievalEnvironment;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.log4j.Logger;

import edu.umd.cloud9.io.array.ArrayListWritable;

public class RandomizedDocNos {
  private static final Logger sLogger = Logger.getLogger(RandomizedDocNos.class);
  Integer numClusters;
  String indexPath;
  RetrievalEnvironment env;
  int numDocs = 0;
  FileSystem fs; 
  private ArrayList<IntWritable> initialCentroidDocs;
  Configuration conf;
  Path optional = null;
  
  public RandomizedDocNos(Configuration conf2){   
    conf = conf2;
    numClusters = conf.getInt("Ivory.KmeansClusterCount", 5);
    indexPath = conf.get("Ivory.IndexPath");
    
    try {
      fs = FileSystem.get(conf);
      env = new RetrievalEnvironment(indexPath, fs);
      numDocs = env.readCollectionDocumentCount();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    initialCentroidDocs = new ArrayListWritable<IntWritable>();
    
  }
  
  public RandomizedDocNos(JobConf conf2){   
    conf = conf2;
    numClusters = conf.getInt("Ivory.KmeansClusterCount", 5);
    indexPath = conf.get("Ivory.IndexPath");
    
    try {
      fs = FileSystem.get(conf);
      env = new RetrievalEnvironment(indexPath, fs);
      numDocs = env.readCollectionDocumentCount();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    initialCentroidDocs = new ArrayListWritable<IntWritable>();
    
  }
  
  public RandomizedDocNos(JobConf conf2,Path local){   
    conf = conf2;
    optional = local;
    numClusters = conf.getInt("Ivory.KmeansClusterCount", 5);
    indexPath = conf.get("Ivory.IndexPath");
    
    try {
      fs = FileSystem.get(conf);
      env = new RetrievalEnvironment(indexPath, fs);
      numDocs = env.readCollectionDocumentCount();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    initialCentroidDocs = new ArrayListWritable<IntWritable>();
    
  }
  
  public void getRandomDocs(){
    
    for(int i=0;i<numClusters;i++){
      IntWritable randomNumber = new IntWritable(1 + (int)(Math.random()*numDocs));
      while(initialCentroidDocs.contains(randomNumber)){
        randomNumber.set(1 + (int)(Math.random()*numDocs));
      }
      initialCentroidDocs.add(randomNumber);
    }
    try {
      writeRandomDocs();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      sLogger.info("Failed in writing docnos to file");
      e.printStackTrace();
    }
  }
  
  private void writeRandomDocs() throws IOException{
    Path outFile = new Path(env.getKmeansRandomDocNoPath());
    
    if (fs.exists(outFile)){
      sLogger.info("DocnoDir already exists!");
      return;
    }
    
    FSDataOutputStream out = fs.create(outFile);
    for(IntWritable docno: initialCentroidDocs){
      docno.write(out);
//      out.write(0x00);
    }
    out.close();
  }
  
  public int readRandomDocs(ArrayList<IntWritable> toFill) throws IOException{
    Path inFile;
    if(optional == null){
       inFile = new Path(env.getKmeansRandomDocNoPath());
    }else{
       inFile = optional;
    }
    
   
    sLogger.info("DocnoDir: " + inFile);
    
    if (!fs.exists(inFile)){
      sLogger.info("DocnoDir doesn't exists!");
      return -1;
    }
    FSDataInputStream in = fs.open(inFile);
    
    for(int i=0;i<numClusters;i++){
      IntWritable inreader = new IntWritable();
      inreader.readFields(in);
      toFill.add(inreader);
//      in.readByte();
    }
    
    in.close();
    
  
  return 0;
  }
  
  

}
