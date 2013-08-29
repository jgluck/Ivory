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

package ivory.kmeans.app;

import ivory.kmeans.util.KmeansAggregator;
import ivory.kmeans.util.KmeansBatchQueryRunner;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

/**
 * A version of run query local designed to work on 
 * clustered data from our kmeans solution. Needs to aggregate results
 * and sort them before evaluation
 * 
 * This is done using {@link KmeansAggregator}.
 * @author jgluck
 *
 */
public class KmeansRunQueryLocal {
  private static final Logger LOG = Logger.getLogger(KmeansRunQueryLocal.class);
  private ArrayList<KmeansBatchQueryRunner> runner = null;
  private int numFiles = 0;
  KmeansAggregator aggregator;
  FileSystem fs;

  public KmeansRunQueryLocal(String[] args) throws SAXException, IOException,
      ParserConfigurationException, Exception, NotBoundException {
    runner = new ArrayList<KmeansBatchQueryRunner>();
    String indexdir = args[0]; //we place the eval-index directory as the first argument, then strip it later
    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.getLocal(conf);
    this.fs = fs;
    FileStatus[] files = fs.listStatus(new Path(indexdir)); //list of indexes
    aggregator = new KmeansAggregator(); //create the results aggregator
    this.numFiles=files.length;
    for(int i=0;i<files.length;i++){
      try {
        LOG.info("initilaize runquery ...");
        LOG.info("FILES[i]: "+files[i].getPath().toUri().getPath());
//        runner.add(new KmeansBatchQueryRunner(Arrays.copyOfRange(args, 1, args.length), fs, files[i].getPath().toUri().getPath()));
      KmeansBatchQueryRunner runDoer = new KmeansBatchQueryRunner(Arrays.copyOfRange(args, 1, args.length), fs, files[i].getPath().toUri().getPath());
      runDoer.runQueriesIntermediate(this.aggregator); //we run the job now to save memory
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * runs the queries
   */
  public void runQueries() throws Exception {
    LOG.info("Running the queries ..."); 
//    ArrayList<ArrayList<QueryRunner>> buildup = new ArrayList<ArrayList<QueryRunner>>();
    long start = System.currentTimeMillis();

    aggregator.addFS(this.fs);
    aggregator.finishUp(); //chooses results and prints them to file
    long end = System.currentTimeMillis();

    LOG.info("Total query time: " + (end - start) + "ms");
    System.out.println("Total\t" + (end - start) + " ms");
  }

  public static void main(String[] args) throws Exception {
    KmeansRunQueryLocal s;
    try {
      s = new KmeansRunQueryLocal(args);
      s.runQueries();
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.exit(0);
  }
}
