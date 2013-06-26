package ivory.core.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ivory.core.RetrievalEnvironment;
import ivory.core.data.document.WeightedIntDocVector;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;
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
      fs = FileSystem.getLocal(conf);
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
      fs = FileSystem.getLocal(conf2);
//      env = new RetrievalEnvironment(indexPath, fs);
//      numDocs = env.readCollectionDocumentCount();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      sLogger.info("Failed in settin gup fs");
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
      // find the last / in inFile's name and shorten to that then get the directory data
      sLogger.info("DirectoryListing: " + fs.listFiles(inFile.getParent().getParent().getParent().getParent(), false));
      sLogger.info("DirectoryListing: " + fs.listFiles(inFile.getParent().getParent().getParent(), false));
      sLogger.info("DirectoryListing: " + fs.listFiles(inFile.getParent().getParent(), false));
      sLogger.info("DirectoryListing: " + fs.listFiles(inFile.getParent(), false));
      sLogger.info("File Status: " + fs.getFileStatus(inFile));
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
  
  public static int readSequenceFile(Path path, FileSystem fs, int max) throws IOException {
    SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, fs.getConf());

    System.out.println("Reading " + path + "...\n");
    try {
      System.out.println("Key type: " + reader.getKeyClass().toString());
      System.out.println("Value type: " + reader.getValueClass().toString() + "\n");
    } catch (Exception e) {
      throw new RuntimeException("Error: loading key/value class");
    }

    Writable key, value;
    int n = 0;
    try {
      key = (Writable) reader.getKeyClass().newInstance();
      value = (Writable) reader.getValueClass().newInstance();

      while (reader.next(key, value)) {
        System.out.println("Record " + n);
        System.out.println("Key: " + key + "\nValue: " + value);
        System.out.println("----------------------------------------");
        n++;

        if (n >= max)
          break;
      }
      reader.close();
      System.out.println(n + " records read.\n");
    } catch (Exception e) {
      e.printStackTrace();
    }

    return n;
  }
  
  public static void getSequenceFiles(String path, int maxnumrecords) throws IOException {

    String f = path;

    int max = Integer.MAX_VALUE;


    FileSystem fs = FileSystem.get(new Configuration());
    Path p = new Path(f);

    if (fs.getFileStatus(p).isDir()) {
      readSequenceFilesInDir(p, fs, max);
    } else {
      readSequenceFile(p, fs, max);
    }
  }
  
  private static int readSequenceFilesInDir(Path path, FileSystem fs, int max) {
    int n = 0;
    try {
      FileStatus[] stat = fs.listStatus(path);
      for (int i = 0; i < stat.length; ++i) {
        n += readSequenceFile(stat[i].getPath(), fs ,max);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    System.out.println(n + " records read in total.");
    return n;
  }

public List<Path> getSequenceFileList(String p, final String filter, FileSystem fs) throws FileNotFoundException, IOException{
  FileStatus[] fss = fs.listStatus(new Path(p));
  List<Path> matches = new LinkedList<Path>();
  for (FileStatus status : fss) {
      Path path = status.getPath();
      if (path.toString().contains(filter)){
        matches.add(path);
      }
  }
  return matches;
}

public void collectCentroids(){
  List<Path> paths = null;
//  List<WeightedIntDocVector> centroids = new ArrayList<WeightedIntDocVector>();
  Path outFile = new Path(env.getCurrentCentroidPath());
  String p = env.getKmeansCentroidDirectory();
  try {
    if (fs.exists(outFile)){
      sLogger.info("DocnoDir already exists!");
      return;
    }
  } catch (IOException e2) {
    // TODO Auto-generated catch block
    e2.printStackTrace();
  }
  
  FSDataOutputStream out = null;
  try {
    out = fs.create(outFile);
  } catch (IOException e1) {
    // TODO Auto-generated catch block
    e1.printStackTrace();
  }
//  FileSystem fs = FileSystem.get(conf)
  try {
     paths = getSequenceFileList(p,"part-",fs);
  } catch (FileNotFoundException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  } catch (IOException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
   for(Path path: paths){
     try {
      SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, conf);
      IntWritable key = new IntWritable();
      WeightedIntDocVector value = new WeightedIntDocVector();
      while (reader.next(key, value)) {
        value.write(out);
    }
      reader.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
   }
   try {
    out.close();
  } catch (IOException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
}
  
  

}
