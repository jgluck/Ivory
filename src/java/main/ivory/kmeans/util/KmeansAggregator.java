package ivory.kmeans.util;

import ivory.core.util.ResultWriter;
import ivory.smrf.retrieval.Accumulator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.hadoop.fs.FileSystem;
import org.apache.log4j.Logger;

import org.apache.commons.lang.ArrayUtils;

import edu.umd.cloud9.collection.DocnoMapping;

/**
 * collects results from query runners. outputs them to file
 * @author jgluck
 *
 */
public class KmeansAggregator {
  private static final Logger LOG = Logger.getLogger(KmeansAggregator.class);
  DocnoMapping docnoMapping = null;
  HashMap<Integer,ArrayList<Map<String,Accumulator[]>>> results;
  HashMap<Integer,Map<String,Accumulator[]>>finalResults;
  Map<String,String> queries;
  ArrayList<String> models;
  HashMap<Integer,String> filenameMap;
  HashMap<Integer,Boolean>compressMap;
  FileSystem fs;

//  ArrayList<Accumulator[]> results;
  
  public KmeansAggregator(){
    this.results = new HashMap<Integer,ArrayList<Map<String,Accumulator[]>>>();
    this.finalResults = new HashMap<Integer,Map<String,Accumulator[]>>();
    this.queries = new HashMap<String,String>();
    this.filenameMap = new HashMap<Integer,String>();
    this.compressMap = new HashMap<Integer,Boolean>();
    this.models = new ArrayList<String>();
  }
  
  public void addQueries(Map<String,String> q){
    if(this.queries.isEmpty()){
      this.queries=q;
    }
  }
  
  public void addDocnoMapping(DocnoMapping d){
    if(this.docnoMapping==null){
      this.docnoMapping=d;
    }
  }
  
  public void addFS(FileSystem f){
    this.fs = f;
  }
  
  /**
   * Adds results from a query runner. 
   * Maps query strings to
   * accumulator arrays
   * @param mid
   * @param innerResults
   */
  public void addResults(String mid, Map<String,Accumulator[]> innerResults){
    if(this.finalResults.keySet().contains(mid.hashCode())){
      this.flattenResults(this.finalResults.get(mid.hashCode()), innerResults);
    }else{
      this.finalResults.put(mid.hashCode(), innerResults);
      this.models.add(mid);
    }
  }
  
  /**
   * Concatenates accumulators. 
   * @param innermap
   * @param innerResults
   */
  public void flattenResults(Map<String,Accumulator[]> innermap, Map<String,Accumulator[]>innerResults){
    for(String queryID: this.queries.keySet()){
      Accumulator[] tmp = innerResults.get(queryID);
      if(innermap.containsKey(queryID)){
        Accumulator[] tmp2 = innermap.get(queryID);
        innermap.put(queryID, (Accumulator[]) ArrayUtils.addAll(tmp2, tmp));
      }else{
        innermap.put(queryID, tmp);
      }
    }
  }
  
  /**
   * For each query write the results to the correct file
   * @throws IOException
   */
  public void finishUp() throws IOException{
    for(String mod: this.models){
      //for each model do this
      String fn = this.filenameMap.get(mod.hashCode());

      boolean compress = this.compressMap.get(mod.hashCode());
      ResultWriter resultWriter = new ResultWriter(fn, compress, fs);

      for(String queryID: this.queries.keySet()){
        Accumulator[] collector = null;

        collector = this.finalResults.get(mod.hashCode()).get(queryID);
        if (collector == null) {
          LOG.info("null results for: " + queryID);
          continue;
        }
        Arrays.sort(collector,Collections.reverseOrder());
//        Arrays.sort(collector);
        int todo =0; // LIMIT NUMBER OF RESULTS PER QUERY 0 allow all through
        if(collector.length<todo){
          todo = collector.length;
        }else if(todo==0){
          todo = collector.length;
        }
        if (docnoMapping == null) {
          for (int i = 0; i < todo; i++) {
            resultWriter.println(queryID + " Q0 " + collector[i].docno + " " + (i + 1) + " "
                + collector[i].score + " " + mod);
          }
        }else{
          for (int i = 0; i < todo; i++) {
            resultWriter.println(queryID + " Q0 " + docnoMapping.getDocid(collector[i].docno)
                + " " + (i + 1) + " " + collector[i].score + " " + mod);
          }
        }
      }
      resultWriter.flush();
    }
  }
  
  /**
   * Only occurs once per model, adds output options for each model.
   * @param mid
   * @param fn
   * @param cp
   */
  public void addOutputOptions(String mid, String fn, boolean cp){
    if(this.filenameMap.get(mid.hashCode())==null){
      this.filenameMap.put(mid.hashCode(), fn);
      this.compressMap.put(mid.hashCode(), cp);
    }
  }
  

}
