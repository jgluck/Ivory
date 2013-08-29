/*
 * Ivory: A Hadoop toolkit for web-scale information retrieval research
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


package ivory.kmeans.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ivory.core.Constants;
import ivory.core.RetrievalEnvironment;
import ivory.core.data.document.DocnoWeightedIntDocVectorPair;
import ivory.core.data.document.IntDocVector;
import ivory.core.data.document.LazyIntDocVector;
import ivory.core.data.document.WeightedIntDocVector;
import ivory.core.data.stat.DfTableArray;
import ivory.core.data.stat.DocLengthTable;
import ivory.core.data.stat.DocLengthTable2B;
import ivory.core.data.stat.DocLengthTable4B;
import ivory.core.util.CLIRUtils;
import ivory.pwsim.score.ScoringModel;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.log4j.Logger;
import org.mortbay.log.Log;

import edu.umd.cloud9.collection.DocnoMapping;
import edu.umd.cloud9.collection.Indexable;
import edu.umd.cloud9.collection.trec.TrecDocnoMapping;
import edu.umd.cloud9.collection.trec.TrecDocument;
import edu.umd.cloud9.collection.trec.TrecDocumentInputFormat;
import edu.umd.cloud9.io.array.ArrayListWritable;
import edu.umd.cloud9.io.map.HMapIFW;
import edu.umd.cloud9.util.map.MapIF;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Jon Gluck
 * 
 * This class contains all of the utility functions for KMeans experiments in Ivory
 */

public class KmeansUtility {
  private static final Logger sLogger = Logger.getLogger(KmeansUtility.class);
  Integer numClusters;
  String indexPath;
  RetrievalEnvironment env;
  int numDocs = 0;
  FileSystem fs; 
  private ArrayList<IntWritable> initialCentroidDocs;
  Configuration conf;
  Path optional = null;

  
  /**Begin Constructors**/
  
  public KmeansUtility(Configuration conf2){   
    conf = conf2;
    numClusters = conf.getInt("Ivory.KmeansClusterCount", 5);
    indexPath = conf.get("Ivory.IndexPath");
    
    try {
      fs = FileSystem.get(conf);
      env = new RetrievalEnvironment(indexPath, fs);
      numDocs = env.readCollectionDocumentCount();
    } catch (IOException e) {
      sLogger.info("Failed to retrieve Filesystem/retrieval environment");
      e.printStackTrace();
    }
    
    initialCentroidDocs = new ArrayListWritable<IntWritable>();
    
  }
  
  public KmeansUtility(JobConf conf2){   
    conf = conf2;
    numClusters = conf.getInt(Constants.KmeansClusterCount, 5);
    indexPath = conf.get(Constants.IndexPath);
    
    try {
      fs = FileSystem.getLocal(conf);
      env = new RetrievalEnvironment(indexPath, fs);
      numDocs = env.readCollectionDocumentCount();
    } catch (IOException e) {
      sLogger.info("Failed to retrieve Filesystem/retrieval environment");
      e.printStackTrace();
    }
    initialCentroidDocs = new ArrayListWritable<IntWritable>();    
  }
  
  public KmeansUtility(JobConf conf2,Path local){   
    conf = conf2;
    optional = local;
    numClusters = conf.getInt(Constants.KmeansClusterCount, 5);
    indexPath = conf.get(Constants.IndexPath);
    
    try {
      fs = FileSystem.getLocal(conf2);
      //Not sure following two lines were meant to be commented out
//      env = new RetrievalEnvironment(indexPath, fs);
//      numDocs = env.readCollectionDocumentCount();
    } catch (IOException e) {
      sLogger.info("Failed to retrieve Filesystem/retrieval environment");
      e.printStackTrace();
    }
    
    initialCentroidDocs = new ArrayListWritable<IntWritable>();
    
  }
  
  /*
   * GetRandomDocs:
   * Generates a list of document numbers which will serve as initial clustering points.
   */
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
      sLogger.info("Failed in writing docnos to file");
      e.printStackTrace();
    }
  }
  
  
  /*
   * writeRandomDocs:
   * Writes the generated list of initial centroid numbers to the store, for later retrieval in a MapReuce Job
   */
  private void writeRandomDocs() throws IOException{
    Path outFile = new Path(env.getKmeansRandomDocNoPath());
    
    if (fs.exists(outFile)){
      sLogger.info("DocnoDir already exists!");
      return;
    }
    
    FSDataOutputStream out = fs.create(outFile);
    for(IntWritable docno: initialCentroidDocs){
      docno.write(out);
    }
    out.close();
  }
  
  /*
   * readRandomDocs:
   * Reads the initial centroid document numbers from the store. This is usually called by a Mapper in a setup procedure
   */
  public int readRandomDocs(ArrayList<IntWritable> toFill) throws IOException{
    Path inFile;
    
    if(optional == null){
      //We haven't been given a specific path
       inFile = new Path(env.getKmeansRandomDocNoPath());
    }else{
      //We're in distributed cache, it would seem.
       inFile = optional;
    }  
    sLogger.info("Intitial Centroid Number File: " + inFile);
    
    if (!fs.exists(inFile)){
      sLogger.info("Initial Centroid Number File doesn't exists!");
      return -1;
    }
    FSDataInputStream in = fs.open(inFile);
    
    for(int i=0;i<numClusters;i++){
      IntWritable inreader = new IntWritable();
      inreader.readFields(in);
      toFill.add(inreader);
    }
    
    in.close();
    return 0;
  }
  
  /*
   * readSequenceFile:
   * A Helper method for reading out a sequence file for debugging.
   * Will read out a given sequence file to standard out.
   */
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
        sLogger.info("Record " + n);
        sLogger.info("Key: " + key + "\nValue: " + value);
        sLogger.info("----------------------------------------");
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
  
  
  /*
   * getSequenceFiles:
   * Helper function which will read all sequence files at a path, whether a dir or a single file 
   */
  public static void readSequenceFiles(String path, int maxnumrecords) throws IOException {

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
  
  /*
   * readSequenceFilesInDir:
   * Helper function which will read all sequence files in a directory
   */
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

  /*
   * getSequenceFileList:
   * returns a list of sequence files contained at a path
   * give it a filter, and will return a list of paths that match that filter
   */
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


  /*
   * readCurrentCentroids:
   * takes an empty arraylist called toFill, and fills it with the current actual centroids. Not their document numbers.
   */
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
  }
  in.close();
  return 0;
}

 /*
  * collectCentroids:
  * This gets run between every kMeans step. It opens the output of the previous reducer,
  * which had averaged the points in each cluster. It collects these average centroid points into 
  * that one centroid file again, so that readCurrentCentroids will work.
  */
public void collectCentroids(){
  List<Path> paths = null;
  Path outFile = new Path(env.getCurrentCentroidPath());
  String p = env.getKmeansCentroidDirectory();
  
  FSDataOutputStream out = null;
  try {
    out = fs.create(outFile);
  } catch (IOException e1) {
    sLogger.info("Failed to create file object for currentcentroidpath");
    e1.printStackTrace();
  }

  try {
     paths = getSequenceFileList(p,"part-",fs);
  } catch (FileNotFoundException e) {
    sLogger.info("Path not found error in getting the list of sequence files");
    e.printStackTrace();
  } catch (IOException e) {
    sLogger.info("IO Error in getting the list of sequence files");
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
      sLogger.info("Failed while reading in centroids and writing them back out.");
      e.printStackTrace();
    }
   }
   
   try {
    out.close();
  } catch (IOException e) {
    sLogger.info("Failed to close currentcentroid file");
    e.printStackTrace();
  }
   
}

/*
 * OutputDocNosTest
 * Just a learning/debugging function. Reads out all intdocvectors
 */
public void OutputDocNosTest(HashMap<Integer,Integer>clusterMap){
  List<Path> paths = null;
  
  try {
    paths = getSequenceFileList(env.getIntDocVectorsDirectory(),"part-",fs);
  } catch (FileNotFoundException e) {
    sLogger.info("Failed to find the files at path");
    e.printStackTrace();
  } catch (IOException e) {
    sLogger.info("Failed to list files in directory");
    e.printStackTrace();
  }
  
  for(Path path: paths){
    Log.info("Checking IntDocVector Path: "+path);
    try {
      SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, conf);
      IntWritable key = new IntWritable();
      LazyIntDocVector value = new LazyIntDocVector();
      reader.next(key, value);
      Log.info("Outputting first docno of: " + path + " which is: " + key+ " which should belong to cluster: "+ clusterMap.get(key.get()));
      reader.close();
    }catch (IOException e) {
      sLogger.info("Something went wrong here");
      e.printStackTrace();
    }
  
  }
  
}

/*
 * packLazyVectors:
 * Takes the IntDocVectors and reshuffles them into their packs.
 */
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
    sLogger.info("Failed to create directory for cluster packs");
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
      sLogger.info("Looks like we failed creating a sequence file writer for path: " + outPaths.get(i));
      e.printStackTrace();
    }
    

    outStreams.set(i, out);  
  }
  
   try {
    paths = getSequenceFileList(env.getIntDocVectorsDirectory(),"part-",fs);
  } catch (FileNotFoundException e) {
    sLogger.info("Could not find intdocvectors directory");
    e.printStackTrace();
  } catch (IOException e) {
    sLogger.info("Failed getting file listing?");
    e.printStackTrace();
  }
   for(Path path: paths){
     sLogger.info("Reading in IntDocVector Path: "+path);
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
       sLogger.info("Failed to close paths");
       e.printStackTrace();
     }
   }
   
   try {
     for(SequenceFile.Writer outIterator: outStreams){
       outIterator.close();
     }
   } catch (IOException e) {
     sLogger.info("Failed to close iterator");
     e.printStackTrace();
   }
   
   for(int i = 0;i<numPacks;i++){
     env.writePackDocumentCount(i, packDocCounts[i]);
   }
   
}

/*
 * packLazyVectorEval:
 * Just like PackLazyVector but instead of doing it in the root directory, pack it back down into the eval directory
 * This will not work yet, as eval isnt' implemented, but this function does pack correctly
 */
public void packLazyVectorsEval(HashMap<Integer,Integer> docnoToClusterMapping, HashMap<Integer,Integer> clusterMapping){
  env.createPackDocumentCountDir();
  int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
  List<Path> paths = null;
  SequenceFile.Writer out;
  int[] packDocCounts = new int[numPacks];
  ArrayList<Path> outPaths = new ArrayList<Path>();
  ArrayList<SequenceFile.Writer> outStreams = new ArrayList<SequenceFile.Writer>();
  
  for(int i=0;i<numPacks;i++){
    packDocCounts[i] = 0;
  }
  
  try {
    fs.mkdirs(new Path(env.getOverIndex()));
  } catch (IOException e2) {
    Log.info("Failed to create directory for cluster packs");
    e2.printStackTrace();
  }
  
  for(int i=0;i<numPacks;i++){
    try {
      fs.mkdirs(new Path(env.getClusterPackContainerPathEval(i)));
    } catch (IOException e1) {
      sLogger.info("Failed to make parent directory");
      e1.printStackTrace();
    }
    outPaths.add(new Path(env.getClusterPackPathEval(i)));
    outStreams.add(null);
    out = null;
    try {
      out = SequenceFile.createWriter(fs,conf,outPaths.get(i),IntWritable.class,LazyIntDocVector.class);
      
    } catch (IOException e) {
      Log.info("Looks like we failed creating a sequence file writer for path: " + outPaths.get(i));
      e.printStackTrace();
    }
    outStreams.set(i, out);  
  }
   try {
    paths = getSequenceFileList(env.getIntDocVectorsDirectory(),"part-",fs);
  } catch (FileNotFoundException e) {
    sLogger.info("Failed to find the parent directory?");
    e.printStackTrace();
  } catch (IOException e) {
    sLogger.info("Failed in listing files?");
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
       sLogger.info("Failed to close the reader");
       e.printStackTrace();
     }
   }
   
   try {
     for(SequenceFile.Writer outIterator: outStreams){
       outIterator.close();
     }
   } catch (IOException e) {
     sLogger.info("Failed to close sequencefilewriter");
     e.printStackTrace();
   }
   
   for(int i = 0;i<numPacks;i++){
     env.writePackDocumentCountEval(i, packDocCounts[i]);
   }
}

/*
 * PrepareEvalDirs:
 * I'm not certain this method is worth anything. It exists only to copy files from the main index to the sub indices.
 */
public void PrepareEvalDirs(){
  int curPack = 0;
  FileUtil fileHandler = new FileUtil();
  int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
  List<Path> paths = null;
  paths = new ArrayList<Path>();
//  paths.add(new Path(env.appendPath(indexPath,"property.CollectionAverageDocumentLength")));
//  paths.add(new Path(env.appendPath(indexPath,"property.CollectionName")));
//  paths.add(new Path(env.appendPath(indexPath,"property.CollectionTermCount")));
//  paths.add(new Path(env.appendPath(indexPath,"property.Tokenizer")));
//   
  paths.add(new Path(env.appendPath(indexPath, "docno-mapping.dat")));
//  paths.add(new Path(env.appendPath(indexPath, "property.CollectionDocumentCount")));
  for(int i=0;i<numPacks;i++){
    Path curPackDir = new Path(env.getCurrentIndex(i));
    for(Path p: paths){
      try {
        fileHandler.copy(fs, p, fs, curPackDir, false, true, conf);
      } catch (IOException e) {
        Log.info("Failed to copy file: "+p);
        e.printStackTrace();
      }
    }
  }
}


/**
 * bringPackMapping:
 * @param docnoToClusterMapping
 * @param clusterMapping
 * This function handles creating the mappings from document number to cluster and from cluster to pack.
 */
public void bringPackMapping(HashMap<Integer,Integer> docnoToClusterMapping, HashMap<Integer,Integer> clusterMapping){
  int curPack = 0;
  List<Path> paths = null;
  int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
  
  try {
    paths = getSequenceFileList(env.getKmeansFinalDirectory(),"part-",fs);

  } catch (FileNotFoundException e) {
    sLogger.info("Failed to find parent dir?");
    e.printStackTrace();
  } catch (IOException e) {
    sLogger.info("Failed to read listing?");
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
      sLogger.info("Failed in the reading or the closing");
      e.printStackTrace();
    }
  }
  
  
}

/**
 * packClusters:
 * @param clusterMapping
 * Packs clusters into readable files.
 */
public void packClusters(HashMap<IntWritable,IntWritable> clusterMapping){
  int curPack = 0;
  int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
  List<Path> paths = null;
  FSDataOutputStream out;
  ArrayList<Path> outPaths = new ArrayList<Path>();
  ArrayList<FSDataOutputStream> outStreams= new ArrayList<FSDataOutputStream>();
  
  try {
    fs.mkdirs(new Path(env.getClusterPackDir()));
  } catch (IOException e2) {
    Log.info("Failed to create directory for cluster packs");
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
    sLogger.info("Failed to find parent dir?");
    e.printStackTrace();
  } catch (IOException e) {
    sLogger.info("Failed to read file listing?");
    e.printStackTrace();
  }
   for(Path path: paths){
     try {
       SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, conf);
       IntWritable key = new IntWritable();
       IntWritable value = new IntWritable();
       while (reader.next(key, value)) {
         if(!clusterMapping.containsKey(key)){
           clusterMapping.put(key, new IntWritable(curPack));
           value.write(outStreams.get(curPack));
           curPack = (curPack + 1)%100;
         }else{
           value.write(outStreams.get(clusterMapping.get(key).get()));
         }
       }
       reader.close();
     } catch (IOException e) {
       sLogger.info("Failed somewhere in reading or closing or writing");
       e.printStackTrace();
     }
   }
   
   try {
     for(FSDataOutputStream outIterator: outStreams){
       outIterator.close();
     }
   } catch (IOException e) {
     sLogger.info("Failed in closing outstreams");
     e.printStackTrace();
   }
}

/**
 * packClusters:
 * @param clusterMapping
 * Packs clusters into readable files for evaluation. This will put them into the subindices
 * This method is another one that is intended pre-evaluation
 */
public void packClustersEval(HashMap<IntWritable,IntWritable> clusterMapping){
int curPack = 0;
int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
List<Path> paths = null;
FSDataOutputStream out;
ArrayList<Path> outPaths = new ArrayList<Path>();
ArrayList<FSDataOutputStream> outStreams= new ArrayList<FSDataOutputStream>();

try {
  fs.mkdirs(new Path(env.getOverIndex()));
} catch (IOException e2) {
  sLogger.info("Failed to create directory for overIndex");
  e2.printStackTrace();
}

for(int i=0;i<numPacks;i++){
  try {
    fs.mkdirs(new Path(env.getClusterPackContainerPathEval(i)));
  } catch (IOException e) {
    sLogger.info("Failed to create this index-trec-"+i);
    e.printStackTrace();
  }
  outPaths.add(new Path(env.getClusterPackPathEval(i)));
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
  sLogger.info("Failed to find parent dir?");
  e.printStackTrace();
} catch (IOException e) {
  sLogger.info("failed to get listing?");
  e.printStackTrace();
}
 for(Path path: paths){
   try {
     SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, conf);
     IntWritable key = new IntWritable();
     IntWritable value = new IntWritable();
     while (reader.next(key, value)) {
       if(!clusterMapping.containsKey(key)){
         clusterMapping.put(key, new IntWritable(curPack));
         value.write(outStreams.get(curPack));
         curPack = (curPack + 1)%100;
       }else{
         value.write(outStreams.get(clusterMapping.get(key).get()));
       }
     }
     reader.close();
   } catch (IOException e) {
     sLogger.info("Failed at closing reader");
     e.printStackTrace();
   }
 }
 
 try {
   for(FSDataOutputStream outIterator: outStreams){
     outIterator.close();
   }
 } catch (IOException e) {
   sLogger.info("Failed to close iterator");
   e.printStackTrace();
 }
}


/**
 * whichCluster: 
 * @param initialCentroids
 * @param doc
 * @return cluster number for doc
 * 
 * This function returns which cluster a given doc would belong to.
 */
public int whichCluster(ArrayList<WeightedIntDocVector> initialCentroids, WeightedIntDocVector doc){
  float max = -500;
  float similarity = 0;
  int clusterChoice = -1;
//  IntWritable cluster = new IntWritable(-1);
  int it = 0;
  for(WeightedIntDocVector centroid: initialCentroids){
    similarity = CLIRUtils.cosine(doc, centroid);
    if(similarity>max){
      max = similarity;
      clusterChoice = it;
    }
    it= it +1;
  }
  return clusterChoice;
}


/**
 * convertLazyIntVToWeightedIntV
 * @param original
 * @param docno
 * @return a converted weightedintdocvector
 * 
 * Uncertain if this works. Do not use without reading.
 */
public WeightedIntDocVector convertLazyIntVToWeightedIntV(LazyIntDocVector original, int docno){
  DocLengthTable mDLTable = null;
  ScoringModel mScoreFn = null;
  DfTableArray mDFTable = null;
  
  int docLen = 0;
  
  String dfFile;
  String cfFile;
  String dlFile;
  
  boolean normalize = false;
  boolean shortDocLengths = false;
  normalize = conf.getBoolean("Ivory.Normalize", false);
  shortDocLengths = conf.getBoolean("Ivory.ShortDocLengths", true);
  
  
  dfFile = env.getDfByIntData();
  cfFile = env.getCfByIntData();
  dlFile = env.getDoclengthsData().toString();
  
  try {
    mDFTable = new DfTableArray(new Path(dfFile),fs);
  } catch (IOException e) {
    Log.info("Failed to get dftable array from: "+ dfFile);
    e.printStackTrace();
  } 
  
  try {
    if (shortDocLengths)
      mDLTable = new DocLengthTable2B(new Path(dlFile), FileSystem.getLocal(conf));
    else
      mDLTable = new DocLengthTable4B(new Path(dlFile), FileSystem.getLocal(conf));
  } catch (IOException e1) {
    Log.info("Error loading dl table from " + dlFile);
    e1.printStackTrace();
  }
  
  try {
    mScoreFn = (ScoringModel) Class.forName(conf.get("Ivory.ScoringModel")).newInstance();

    // this only needs to be set once for the entire collection
    mScoreFn.setDocCount(mDLTable.getDocCount());
    mScoreFn.setAvgDocLength(mDLTable.getAvgDocLength());
  } catch (Exception e) {
    throw new RuntimeException("Error initializing Ivory.ScoringModel from "
        + conf.get("Ivory.ScoringModel"));
  }
  
  
  HMapIFW vectorWeights = new HMapIFW();
  
  docLen = mDLTable.getDocLength(docno);
  
  IntDocVector.Reader r = null;
  try {
    r = original.getReader();
  } catch (IOException e) {
    sLogger.info("failed to get reader");
    e.printStackTrace();
  }
  float wt, sum;
  int term;
  sum = 0;
  while (r.hasMoreTerms()){
    term = r.nextTerm();
    mScoreFn.setDF(mDFTable.getDf(term));
    wt = mScoreFn.computeDocumentWeight(r.getTf(), docLen);
    vectorWeights.put(term, wt);
    sum += wt * wt;
  }
  
  if (normalize) {
    /* length-normalize doc vectors */
    sum = (float) Math.sqrt(sum);
    for (MapIF.Entry e : vectorWeights.entrySet()) {
      float score = vectorWeights.get(e.getKey());
      vectorWeights.put(e.getKey(), score / sum);
    }
  }
  WeightedIntDocVector weightedVector = new WeightedIntDocVector(docLen, vectorWeights);
  
  return weightedVector;
}

/**
 * writePackContents
 * @param clusterPackMap
 * @param docnoToClusterMap
 * 
 * writes documentnumbers to each index for later processing
 */
public void writePackContents(HashMap<Integer,Integer> clusterPackMap, HashMap<Integer,Integer> docnoToClusterMap){
  ArrayList<Path> outPaths = new ArrayList<Path>();
  ArrayList<FSDataOutputStream> outStreams= new ArrayList<FSDataOutputStream>();
  int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
  ArrayList<Integer> counts = new ArrayList<Integer>();
  
  
  for(int i=0;i<numPacks;i++){
    outPaths.add(new Path(env.getOldPackDocnoContents(i)));
    
    try {
      outStreams.add(fs.create(outPaths.get(i)));
    } catch (IOException e) {
      sLogger.info("Failed to create ouput stream");
      e.printStackTrace();
    }
    
    counts.add(0);
    
  }
  
  IntWritable writer = new IntWritable();
  
  for(Integer docno: docnoToClusterMap.keySet()){
    writer.set(docno);
    try {
      int packno = clusterPackMap.get(docnoToClusterMap.get(docno));
      writer.write(outStreams.get(packno));
      counts.set(packno, counts.get(packno)+1);
    } catch (IOException e) {
      sLogger.info("Failed to write out docno to pack contents file");
      e.printStackTrace();
    }
  }
  
  for(int i=0;i<numPacks;i++){
    try {
      outStreams.get(i).flush();
      outStreams.get(i).close();
      env.writePackDocnoCount(i, counts.get(i));
    } catch (IOException e) {
      sLogger.info("Failed in flushing and closing outstreams");
      e.printStackTrace();
    }
    
  }
  
}


public int readPackContentsWithPath(ArrayList<Integer> toFill, Integer packNumber,Path p,FileSystem fs2) throws IOException{
  Path inPath = p;
  sLogger.info("Path should be: "+inPath);
  Integer numDocnos = conf.getInt("Ivory.PackDocCount", 0);
  if (!fs2.exists(inPath)){
    sLogger.info("packDocNoContents doesn't exists!");
    return -1;
  }
  FSDataInputStream in = fs2.open(inPath);
  IntWritable reader = new IntWritable();
  for(int i = 0; i < numDocnos; i++){
    reader.readFields(in);
    toFill.add(reader.get());
  }
  return 0;
}


public int readPackContents(ArrayList<Integer> toFill, Integer packNumber) throws IOException{
  Path inPath = new Path(env.getPackDocnoContents());
  Integer numDocnos = env.readPackDocnoCount();
  if (!fs.exists(inPath)){
    sLogger.info("packDocNoContents doesn't exists!");
    return -1;
  }
  FSDataInputStream in = fs.open(inPath);
  IntWritable reader = new IntWritable();
  for(int i = 0; i < numDocnos; i++){
    reader.readFields(in);
    toFill.add(reader.get());
  }
  return 0;
}

/**
 * writePackMapping
 * @param clusterPackMap
 * @param docnoToClusterMap
 * Function to write the cluster to pack mapping and the docno to cluster mapping to the store.
 */
public void writePackMapping(HashMap<Integer,Integer> clusterPackMap, HashMap<Integer,Integer> docnoToClusterMap){
  Path pathPackMapping = new Path(env.getKmeansPackMapping());
  Path pathDocnoMapping = new Path(env.getKmeansDocnoMapping());
  
  SequenceFile.Writer outPackMapping = null;
  SequenceFile.Writer outDocnoMapping = null;
  
  IntWritable key = new IntWritable();
  IntWritable value = new IntWritable();
  
  try {
    outPackMapping = SequenceFile.createWriter(fs,conf,pathPackMapping,IntWritable.class,IntWritable.class);
    outDocnoMapping = SequenceFile.createWriter(fs,conf,pathDocnoMapping,IntWritable.class,IntWritable.class);    
  } catch (IOException e1) {
    sLogger.info("Failed to create the writers for writing pack mapping");
    e1.printStackTrace();
  }
  
  Iterator packIt = clusterPackMap.entrySet().iterator();
  while(packIt.hasNext()){
    Map.Entry pairs = (Map.Entry) packIt.next();
    key.set((Integer)pairs.getKey());
    value.set((Integer)pairs.getValue());
    
    try {
      outPackMapping.append(key, value);
    } catch (IOException e) {
      sLogger.info("Failed to write keyvalue pair to sequence file");
      e.printStackTrace();
    }       
  }
  
  Iterator docnoIt = docnoToClusterMap.entrySet().iterator();
  while(docnoIt.hasNext()){
    Map.Entry pairs = (Map.Entry) docnoIt.next();
    key.set((Integer)pairs.getKey());
    value.set((Integer)pairs.getValue());    
    try {
      outDocnoMapping.append(key, value);
    } catch (IOException e) {
      sLogger.info("Failed to write keyvalue pair to sequence file");
      e.printStackTrace();
    }       
  }
  try{
  outPackMapping.close();
  outDocnoMapping.close();
  }catch(Exception e){
    e.printStackTrace();
  }
  
}

/**
 * sortDocuments
 * @param clusterPackMap
 * @param docnoToClusterMap
 * @return success
 * 
 * Attempts to use a sax parser to parse the input collection into the packs, making N little input collections
 */
public int sortDocuments(HashMap<Integer,Integer>clusterPackMap, HashMap<Integer,Integer>docnoToClusterMap){
//  String collectionPath = conf.get(Constants.CollectionPath);
//  String collectionPath = "/shared/collections/trec/trec4-5_noCRFR.xml"; //HARD CODED
  String collectionPath = "/user/jdg/trec4-5_noCRFR.xml"; //HARD CODED
  String inputFormat = conf.get(Constants.InputFormat);
  Path collection = new Path(collectionPath);
  int numPacks = conf.getInt(Constants.KmeansPackCount, 10);
  ArrayList<Path> outPaths = new ArrayList<Path>();
  ArrayList<FSDataOutputStream> outStreams= new ArrayList<FSDataOutputStream>();
  FSDataOutputStream out;
  
  for(int i=0;i<numPacks;i++){
    try {
      fs.mkdirs(new Path(env.getclusterPackCollectionPath(i)));
    } catch (IOException e) {
      sLogger.info("Failed make parent directory");
      e.printStackTrace();
    }
    outPaths.add(new Path(env.getClusterPackCollection(i)));
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
    FSDataInputStream inTrec = fs.open(collection);
    SAXParserFactory factory = SAXParserFactory.newInstance();
    SAXParser saxParser = factory.newSAXParser();
    DocnoMapping docMapping;
//    docMapping =
//        (DocnoMapping) Class.forName(conf.get(Constants.DocnoMappingClass)).newInstance();
    docMapping = (DocnoMapping) Class.forName(TrecDocnoMapping.class.getCanonicalName()).newInstance(); //hardcoded
    docMapping.loadMapping(env.getDocnoMappingData(), fs);
    TrecClusterPackHandler handler = new TrecClusterPackHandler(clusterPackMap,docnoToClusterMap,docMapping,outStreams,docMapping);
    saxParser.parse(inTrec, handler);
  } catch (IOException e) {
    Log.info("IO Exception in sax parser");
    e.printStackTrace();
  } catch (ParserConfigurationException e) {
    sLogger.info("Error with Sax Parser");
    e.printStackTrace();
  } catch (SAXException e) {
    sLogger.info("Error with Sax Parser");
    e.printStackTrace();
  } catch (InstantiationException e) {
    sLogger.info("Error instantiating sax parser");
    e.printStackTrace();
  } catch (IllegalAccessException e) {
    sLogger.info("Error in parsing, illegal access");
    e.printStackTrace();
  } catch (ClassNotFoundException e) {
    sLogger.info("Failed to get info of trec class?");
    e.printStackTrace();
  }
  
  
  
  for(int i=0;i<numPacks;i++){
    try {
      outStreams.get(i).flush();
      outStreams.get(i).close();
    } catch (IOException e) {
      sLogger.info("Failed to close stream: "+i);
      e.printStackTrace();
    }
  }
  
  
    
  return -1;
}

/**
 * readPackMapping
 * @param clusterPackMap
 * @param docnoToClusterMap
 * @return success
 * 
 * Reads document to cluster and cluster to pack mappings back into map data structures from store.
 */
public int readPackMapping(HashMap<Integer,Integer>clusterPackMap, HashMap<Integer,Integer> docnoToClusterMap){
  sLogger.info("About to begin reading in saved mappings");
  Path pathPackMapping = new Path(env.getKmeansPackMapping());
  Path pathDocnoMapping = new Path(env.getKmeansDocnoMapping());
  
  try {
    if (!fs.exists(pathPackMapping)){
      sLogger.info("packmappath doesn't exists!");
      // find the last / in inFile's name and shorten to that then get the directory data
      return -1;
    }
  } catch (IOException e1) {
    sLogger.info("Failed at interacting with filesystem?");
    e1.printStackTrace();
    return -1;
  }
  
  try {
    if (!fs.exists(pathDocnoMapping)){
      sLogger.info("docnomappingpath doesn't exists!");
      // find the last / in inFile's name and shorten to that then get the directory data
      return -1;
    }
  } catch (IOException e1) {
    sLogger.info("Failed at interacting with filesystem?");
    e1.printStackTrace();
    return -1;
  }
  
//  FSDataInputStream inPackMapping = fs.open(pathPackMapping);
//  FSDataInputStream inDocnoMapping = fs.open(pathDocnoMapping);
  
  try {
    SequenceFile.Reader reader = new SequenceFile.Reader(fs, pathPackMapping, conf);
    IntWritable key = new IntWritable();
    IntWritable value = new IntWritable();
    while (reader.next(key, value)) {
      clusterPackMap.put(key.get(), value.get());
    }
    reader.close();
  }catch(Exception e){
    e.printStackTrace();
    return -1;
  }
  
  try {
    SequenceFile.Reader reader = new SequenceFile.Reader(fs, pathDocnoMapping, conf);
    IntWritable key = new IntWritable();
    IntWritable value = new IntWritable();
    while (reader.next(key, value)) {
      docnoToClusterMap.put(key.get(), value.get());
    }
    reader.close();
  }catch(Exception e){
    e.printStackTrace();
    return -1;
  }
  return 0;
}


/**
 * collect centroids with special path
 * @param p
 */
public void collectCentroids(String p){
  List<Path> paths = null;
//  List<WeightedIntDocVector> centroids = new ArrayList<WeightedIntDocVector>();
  Path outFile = new Path(env.getCurrentCentroidPath());

  FSDataOutputStream out = null;
  try {
    out = fs.create(outFile);
  } catch (IOException e1) {
    sLogger.info("Failed at interacting with filesystem?");
    e1.printStackTrace();
  }

  try {
     paths = getSequenceFileList(p,"part-",fs);
  } catch (FileNotFoundException e) {
    sLogger.info("Couldn't find the parent directory: "+p);
    e.printStackTrace();
  } catch (IOException e) {
    sLogger.info("failed to get listing of dir?");
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
      sLogger.info("Failed to close reader");
      e.printStackTrace();
    }
   }
   try {
    out.close();
  } catch (IOException e) {
    sLogger.info("Failed to close out stream");
    e.printStackTrace();
  }
}

}
