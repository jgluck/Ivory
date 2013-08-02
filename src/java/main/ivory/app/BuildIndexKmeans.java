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

package ivory.app;

import java.util.ArrayList;
import java.util.HashMap;

import ivory.core.Constants;
import ivory.core.RetrievalEnvironment;
import ivory.core.data.document.LazyIntDocVector;
import ivory.core.data.document.WeightedIntDocVector;
import ivory.core.index.BuildIPInvertedIndexDocSorted;
import ivory.core.index.BuildIntPostingsForwardIndex;
import ivory.core.index.BuildLPInvertedIndexDocSorted;
import ivory.core.index.KmeansBuildIPInvertedIndexDocSorted;
import ivory.core.index.KmeansBuildIntPostingsForwardIndex;
import ivory.core.preprocess.BuildWeightedIntDocVectors;
import ivory.core.preprocess.BuildWeightedTermDocVectors;
import ivory.core.preprocess.KmeansClusterOnCentroids;
import ivory.core.preprocess.KmeansFinalClusterStep;
import ivory.core.preprocess.KmeansGetInitialCentroids;
import ivory.core.util.KmeansUtility;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import org.mortbay.log.Log;

public class BuildIndexKmeans extends Configured implements Tool {
  private static final Logger LOG = Logger.getLogger(BuildIndexKmeans.class);

  public static final String INDEX_PATH = "index";
  public static final String INDEX_PARTITIONS = "indexPartitions";

  public static final String POSITIONAL_INDEX_IP = "positionalIndexIP";
  public static final String POSITIONAL_INDEX_LP = "positionalIndexLP";
  public static final String NONPOSITIONAL_INDEX_IP = "nonpositionalIndexIP";

  @SuppressWarnings({ "static-access" })
//  @Override
  public int run(String[] args) throws Exception {
    Options options = new Options();
    options.addOption(new Option(POSITIONAL_INDEX_IP, "build positional index (IP algorithm)"));
    options.addOption(new Option(POSITIONAL_INDEX_LP, "build positional index (LP algorithm)"));
    options.addOption(new Option(NONPOSITIONAL_INDEX_IP, "build nonpositional index (IP algorithm)"));

    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("(required) index path").create(INDEX_PATH));
    options.addOption(OptionBuilder.withArgName("num").hasArg()
        .withDescription("(optional) number of index partitions: 64 default")
        .create(INDEX_PARTITIONS));

    CommandLine cmdline;
    CommandLineParser parser = new GnuParser();
    try {
      cmdline = parser.parse(options, args);
    } catch (ParseException exp) {
      System.err.println("Error parsing command line: " + exp.getMessage());
      return -1;
    }

    if (!cmdline.hasOption(INDEX_PATH)) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.setWidth(120);
      formatter.printHelp(this.getClass().getName(), options);
      ToolRunner.printGenericCommandUsage(System.out);
      return -1;
    }

    String indexPath = cmdline.getOptionValue(INDEX_PATH);

    int indexPartitions = cmdline.hasOption(INDEX_PARTITIONS) ?
        Integer.parseInt(cmdline.getOptionValue(INDEX_PARTITIONS)) : 64;

    Configuration conf = getConf();

    LOG.info("Tool name: " + this.getClass().getSimpleName());
    LOG.info(String.format(" -%s %s", INDEX_PATH, indexPath));
    LOG.info(String.format(" -%s %d", INDEX_PARTITIONS, indexPartitions));

    if (cmdline.hasOption(POSITIONAL_INDEX_IP)) {
      LOG.info(String.format(" -%s", POSITIONAL_INDEX_IP));
      conf.set(Constants.IndexPath, indexPath);
      conf.setInt(Constants.NumReduceTasks, indexPartitions);
      conf.set(Constants.PostingsListsType,
          ivory.core.data.index.PostingsListDocSortedPositional.class.getCanonicalName());
      //gonna do my stuff here
      
      conf.set("Ivory.ScoringModel", "ivory.pwsim.score.Bm25");
      conf.setBoolean("Ivory.Normalize", true);
      conf.setInt("Ivory.MinNumTerms",5);
      conf.setInt(Constants.KmeansClusterCount, 1000);
      conf.setInt(Constants.NumMapTasks, 100);
      conf.setInt(Constants.KmeansPackCount, 100);
      int exitCode = new BuildWeightedIntDocVectors(conf).run();
//      int exitCode = new BuildWeightedTermDocVectors(conf).run();
      
      conf.set(Constants.KmeansDocumentType, WeightedIntDocVector.class.getCanonicalName());
      
//      conf.setInt(Constants.KmeansClusterCount, 1000);
      ArrayList<WeightedIntDocVector> testArray = new ArrayList<WeightedIntDocVector>();
      
      //get some initial centroid numbers
      
      KmeansUtility docnoRandomizer = new KmeansUtility(conf);
      docnoRandomizer.getRandomDocs();
      
      //uncomment from here
      
//      KmeansGetInitialCentroids getSomeCentroidsTool = new KmeansGetInitialCentroids(conf);
//      int numInitialCentroids = getSomeCentroidsTool.run();
//      LOG.info("Ran my initial Centroids Job returned: " + numInitialCentroids);
//      
//      docnoRandomizer.collectCentroids();
//      
//      FileSystem fs = FileSystem.get(conf);
//      RetrievalEnvironment env = new RetrievalEnvironment(indexPath, fs);
//      
//      //kmeans loop
//      for(int i=0;i<5;i++){
//       
//        conf.setInt("CurrentRun", i);
//        KmeansClusterOnCentroids clusterThoseCentroidsTool = new KmeansClusterOnCentroids(conf);
//        int numNewCentroids = clusterThoseCentroidsTool.run();
//        LOG.info("Number of Initial Centroids: "+numInitialCentroids);
//       
//        docnoRandomizer.collectCentroids(env.getKmeansCentroidDirectory(conf.getInt("CurrentRun", 0)));
////        testArray.clear();
////         docnoRandomizer.readCurrentCentroids(testArray);
////         LOG.info("Centroids after clustering: " +testArray);
////         LOG.info("Length of first object: " + testArray.get(0).getWeightedTerms().size());
//      }
//      KmeansFinalClusterStep finalClusterer = new KmeansFinalClusterStep(conf);
//      int resultOfFinalClusterStep = finalClusterer.run();
      
      //comment up to here
      
     
      
      //comment starts here
//      
      HashMap<Integer,Integer> clusterPackMap = new HashMap<Integer,Integer>();
      HashMap<Integer,Integer> docnoToClusterMap = new HashMap<Integer,Integer>();  
////      docnoRandomizer.bringPackMapping(docnoToClusterMap, clusterPackMap);
////      docnoRandomizer.packLazyVectorsEval(docnoToClusterMap, clusterPackMap);
////      docnoRandomizer.PrepareEvalDirs();
      
////      docnoRandomizer.writePackMapping(clusterPackMap, docnoToClusterMap);
       
////      for(int i=0;i<conf.getInt(Constants.KmeansPackCount, 10);i++){
////        new KmeansBuildIPInvertedIndexDocSorted(conf,i).run();
////      }
//      
      //comment up to here
      
      
       //need to build these for each pack
////      for(int i=0;i<conf.getInt(Constants.KmeansPackCount, 10);i++){
////        new KmeansBuildIntPostingsForwardIndex(conf,i).run();
////      }
      docnoRandomizer.readPackMapping(clusterPackMap, docnoToClusterMap);
      docnoRandomizer.sortDocuments(clusterPackMap, docnoToClusterMap);
      
      
    } else if (cmdline.hasOption(POSITIONAL_INDEX_LP)) {
      LOG.info(String.format(" -%s", POSITIONAL_INDEX_LP));
      conf.set(Constants.IndexPath, indexPath);
      conf.setInt(Constants.NumReduceTasks, indexPartitions);
      conf.set(Constants.PostingsListsType,
          ivory.core.data.index.PostingsListDocSortedPositional.class.getCanonicalName());

      conf.setFloat("Ivory.IndexingMapMemoryThreshold", 0.9f);
      conf.setFloat("Ivory.IndexingReduceMemoryThreshold", 0.9f);
      conf.setInt("Ivory.MaxHeap", 2048);
      conf.setInt("Ivory.MaxNDocsBeforeFlush", 50000);

      new BuildLPInvertedIndexDocSorted(conf).run();
      new BuildIntPostingsForwardIndex(conf).run();
    } else if (cmdline.hasOption(NONPOSITIONAL_INDEX_IP)) {
      LOG.info(String.format(" -%s", NONPOSITIONAL_INDEX_IP));
      conf.set(Constants.IndexPath, indexPath);
      conf.setInt(Constants.NumReduceTasks, indexPartitions);
      conf.set(Constants.PostingsListsType,
          ivory.core.data.index.PostingsListDocSortedNonPositional.class.getCanonicalName());

      new BuildIPInvertedIndexDocSorted(conf).run();
      new BuildIntPostingsForwardIndex(conf).run();
    } else {
      LOG.info(String.format("Nothing to do. Specify one of the following: %s, %s, %s",
          POSITIONAL_INDEX_IP, POSITIONAL_INDEX_LP, NONPOSITIONAL_INDEX_IP));
    }

    return 0;
  }

  /**
   * Dispatches command-line arguments to the tool via the {@code ToolRunner}.
   */
  public static void main(String[] args) throws Exception {
    ToolRunner.run(new BuildIndexKmeans(), args);
  }
}
