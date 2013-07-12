package ivory.core.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import ivory.core.Constants;
import ivory.core.RetrievalEnvironment;
import ivory.core.data.document.DocnoWeightedIntDocVectorPair;
import ivory.core.data.document.IntDocVector;
import ivory.core.data.document.LazyIntDocVector;
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
import org.mortbay.log.Log;

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
//  Class<? extends IntDocVector> VectorClass = null;
  
    
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
//    
//    try {
//      VectorClass = (Class<? extends IntDocVector>) Class.forName(env.readKmeansType());
//    } catch (ClassNotFoundException e1) {
//      // TODO Auto-generated catch block
//      e1.printStackTrace();
//    }
    
    initialCentroidDocs = new ArrayListWritable<IntWritable>();
    
  }
  
  public RandomizedDocNos(JobConf conf2){   
    conf = conf2;
    numClusters = conf.getInt(Constants.KmeansClusterCount, 5);
    indexPath = conf.get(Constants.IndexPath);
    
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
    numClusters = conf.getInt(Constants.KmeansClusterCount, 5);
    indexPath = conf.get(Constants.IndexPath);
    
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
    
    Log.info("Generating random numbers for : " + numClusters + " clusters!!");
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


public int readCurrentCentroids(ArrayList<WeightedIntDocVector> toFill) throws IOException{
  Path inFile;
  if(optional == null){
     inFile = new Path(env.getCurrentCentroidPath());
  }else{
     inFile = optional;
  }
  
  sLogger.info("currentCentroidfilepath: " + inFile);
  
  if (!fs.exists(inFile)){
    sLogger.info("currentcentroidpath doesn't exists!");
    // find the last / in inFile's name and shorten to that then get the directory data
    return -1;
  }
  FSDataInputStream in = fs.open(inFile);
  
  Log.info("Collecting: "+numClusters+ " clusters!!");
  for(int i=0;i<numClusters;i++){
    WeightedIntDocVector inreader;
      inreader = new WeightedIntDocVector();
      inreader.readFields(in);
      toFill.add(inreader);

//    in.readByte();
  }
  
  in.close();
  

return 0;
}


public void collectCentroids(){
  List<Path> paths = null;
  Path outFile = new Path(env.getCurrentCentroidPath());
  String p = env.getKmeansCentroidDirectory();
  
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
      WeightedIntDocVector value =  new WeightedIntDocVector();
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

public void OutputDocNosTest(HashMap<Integer,Integer>clusterMap){
  List<Path> paths = null;
  
  try {
    paths = getSequenceFileList(env.getIntDocVectorsDirectory(),"part-",fs);
  } catch (FileNotFoundException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  } catch (IOException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
  
  for(Path path: paths){
    Log.info("Checking IntDocVector Path: "+path);
    try {
      SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, conf);
      IntWritable key = new IntWritable();
//      WeightedIntDocVector value = new WeightedIntDocVector();
//      DocnoWeightedIntDocVectorPair value = new DocnoWeightedIntDocVectorPair();
      LazyIntDocVector value = new LazyIntDocVector();
      reader.next(key, value);
      Log.info("Outputting first docno of: " + path + " which is: " + key+ " which should belong to cluster: "+ clusterMap.get(key.get()));
      reader.close();
    }catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  
  }
  
}

public void packLazyVectors(HashMap<Integer,Integer> docnoToClusterMapping, HashMap<Integer,Integer> clusterMapping){
  env.createPackDocumentCountDir();
  int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
  List<Path> paths = null;
  SequenceFile.Writer out;
  int[] packDocCounts = new int[numPacks];
  ArrayList<Path> outPaths = new ArrayList<Path>();
  ArrayList<SequenceFile.Writer> outStreams = new ArrayList<SequenceFile.Writer>();
//  ArrayList<FSDataOutputStream> outStreams= new ArrayList<FSDataOutputStream>();
  
  for(int i=0;i<numPacks;i++){
    packDocCounts[i] = 0;
  }
  
  try {
    fs.mkdirs(new Path(env.getClusterPackDir()));
  } catch (IOException e2) {
    Log.info("Failed to create directory for cluster packs");
    // TODO Auto-generated catch block
    e2.printStackTrace();
  }
  
  for(int i=0;i<numPacks;i++){
    env.createPackDocumentDir(i);
    outPaths.add(new Path(env.getClusterPackPath(i)));
    outStreams.add(null);
    out = null;
    try {
      out = SequenceFile.createWriter(fs,conf,outPaths.get(i),IntWritable.class,LazyIntDocVector.class);
      
    } catch (IOException e) {
      // TODO Auto-generated catch block
      Log.info("Looks like we failed creating a sequence file writer for path: " + outPaths.get(i));
      e.printStackTrace();
    }
    

    outStreams.set(i, out);  
  }
  
   try {
    paths = getSequenceFileList(env.getIntDocVectorsDirectory(),"part-",fs);
  } catch (FileNotFoundException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  } catch (IOException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
   for(Path path: paths){
     Log.info("Reading in IntDocVector Path: "+path);
     try {
       SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, conf);
       IntWritable key = new IntWritable();
       LazyIntDocVector value = new LazyIntDocVector();
       while (reader.next(key, value)) {
         int streamIndex = clusterMapping.get(docnoToClusterMapping.get(key.get()));
         outStreams.get(streamIndex).append(key, value);
         packDocCounts[streamIndex]++;
         
       }
       reader.close();
     } catch (IOException e) {
       // TODO Auto-generated catch block
       e.printStackTrace();
     }
   }
   
   try {
     for(SequenceFile.Writer outIterator: outStreams){
       outIterator.close();
     }
   } catch (IOException e) {
     // TODO Auto-generated catch block
     e.printStackTrace();
   }
   
   for(int i = 0;i<numPacks;i++){
     env.writePackDocumentCount(i, packDocCounts[i]);
   }
   
}

public void bringPackMapping(HashMap<Integer,Integer> docnoToClusterMapping, HashMap<Integer,Integer> clusterMapping){
  int curPack = 0;
  List<Path> paths = null;
  int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
  
  try {
    paths = getSequenceFileList(env.getKmeansFinalDirectory(),"part-",fs);
//    Log.info("Paths to check: " + paths);
  } catch (FileNotFoundException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  } catch (IOException e) {
    // TODO Auto-generated catch block
    e.printStackTrace();
  }
  
  for(Path path: paths){
    Log.info("Mapping Packs in Path: "+path);
    try {
      SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, conf);
      IntWritable cluster = new IntWritable();
      IntWritable docno = new IntWritable();
      while (reader.next(cluster, docno)) {
//        Log.info("Checking mapping for key,: " + cluster.hashCode() + " With Docno: "+ docno);
        if(!clusterMapping.containsKey(cluster.get())){
          clusterMapping.put(cluster.get(), curPack);
          docnoToClusterMapping.put(docno.get(), cluster.get());
          curPack = (curPack + 1)%numPacks;
        }else{
          docnoToClusterMapping.put(docno.get(), cluster.get());
        }
      }
      reader.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
  
  
}

//pack clusters into partitions
public void packClusters(HashMap<IntWritable,IntWritable> clusterMapping){
  int curPack = 0;
  int numPacks = 100;
  List<Path> paths = null;
  FSDataOutputStream out;
  ArrayList<Path> outPaths = new ArrayList<Path>();
  ArrayList<FSDataOutputStream> outStreams= new ArrayList<FSDataOutputStream>();
  
  try {
    fs.mkdirs(new Path(env.getClusterPackDir()));
  } catch (IOException e2) {
    Log.info("Failed to create directory for cluster packs");
    // TODO Auto-generated catch block
    e2.printStackTrace();
  }
  
  for(int i=0;i<numPacks;i++){
    outPaths.add(new Path(env.getClusterPackPath(i)));
    outStreams.add(null);
    out = null;
    try {
      out = fs.create(outPaths.get(i));
    } catch (IOException e1) {
      Log.info("FAILED TO CREATE OUTPUT PATH: "+outPaths.get(i));
      e1.printStackTrace();
    }
    outStreams.set(i, out);  
  }
  
   try {
    paths = getSequenceFileList(env.getKmeansFinalDirectory(),"part-",fs);
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
//       WeightedIntDocVector value = new WeightedIntDocVector();
//       DocnoWeightedIntDocVectorPair value = new DocnoWeightedIntDocVectorPair();
       IntWritable value = new IntWritable();
       while (reader.next(key, value)) {
         if(!clusterMapping.containsKey(key)){
           clusterMapping.put(key, new IntWritable(curPack));
           value.write(outStreams.get(curPack));
//           (value.getDocno()).write(outStreams.get(curPack));
//           (value.getVector()).write(outStreams.get(curPack));
           curPack = (curPack + 1)%100;
         }else{
           value.write(outStreams.get(clusterMapping.get(key).get()));
         }
       }
       reader.close();
     } catch (IOException e) {
       // TODO Auto-generated catch block
       e.printStackTrace();
     }
   }
   
   try {
     for(FSDataOutputStream outIterator: outStreams){
       outIterator.close();
     }
   } catch (IOException e) {
     // TODO Auto-generated catch block
     e.printStackTrace();
   }
}

public int whichCluster(ArrayList<WeightedIntDocVector> initialCentroids, WeightedIntDocVector doc){
  float max = -500;
  float similarity = 0;
  IntWritable cluster = new IntWritable(-1);
  int it = 0;
  for(WeightedIntDocVector centroid: initialCentroids){
    similarity = CLIRUtils.cosine(doc, centroid);
    if(similarity>max){
      max = similarity;
      cluster.set(it);
    }
    it= it +1;
  }
  return cluster.get();
}

public void collectCentroids(String p){
  List<Path> paths = null;
//  List<WeightedIntDocVector> centroids = new ArrayList<WeightedIntDocVector>();
  Path outFile = new Path(env.getCurrentCentroidPath());

  FSDataOutputStream out = null;
  try {
    out = fs.create(outFile);
  } catch (IOException e1) {
    // TODO Auto-generated catch block
    e1.printStackTrace();
  }

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
      while (reader.next(key, (WeightedIntDocVector) value)) {
        (value).write(out);
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
