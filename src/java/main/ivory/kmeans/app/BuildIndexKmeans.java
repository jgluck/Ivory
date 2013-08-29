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
 * 
 * The following attempts to run the trec-45 pre-processing with a KMeans spin
 */

package ivory.kmeans.app;

import java.util.HashMap;

import ivory.core.Constants;
import ivory.core.RetrievalEnvironment;
import ivory.core.data.document.WeightedIntDocVector;
import ivory.core.index.BuildIPInvertedIndexDocSorted;
import ivory.core.index.BuildIntPostingsForwardIndex;
import ivory.core.preprocess.BuildDictionary;
import ivory.core.preprocess.BuildIntDocVectors;
import ivory.core.preprocess.BuildIntDocVectorsForwardIndex;
import ivory.core.preprocess.BuildTermDocVectorsForwardIndex;
import ivory.core.preprocess.BuildWeightedIntDocVectors;
import ivory.core.preprocess.ComputeGlobalTermStatistics;
import ivory.core.tokenize.GalagoTokenizer;
import ivory.kmeans.preprocess.KmeansBuildTermDocVectors;
import ivory.kmeans.preprocess.KmeansClusterOnCentroids;
import ivory.kmeans.preprocess.KmeansFinalClusterStep;
import ivory.kmeans.preprocess.KmeansGetInitialCentroids;
import ivory.kmeans.util.KmeansUtility;

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
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import edu.umd.cloud9.collection.trec.TrecDocnoMapping;
import edu.umd.cloud9.collection.trec.TrecDocumentInputFormat;

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
    options.addOption(OptionBuilder.withArgName("packNum").hasArg()
        .withDescription("(required) number of packs").create("numPacks"));
    options.addOption(OptionBuilder.withArgName("clusterNum").hasArg()
        .withDescription("(required) number of clusters").create("numClusters"));
    options.addOption(OptionBuilder.withArgName("clusterSteps").hasArg()
        .withDescription("(required) number of cluster steps").create("numSteps"));

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
    
    int clusters = Integer.parseInt(cmdline.getOptionValue("numClusters"));
    int packs = Integer.parseInt(cmdline.getOptionValue("numPacks"));
    int steps = Integer.parseInt(cmdline.getOptionValue("numSteps"));

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
      /**
       * begin setting kmeans parameters. 
       * Below we have set it to make 100 clusters
       * then pack them into 10 packs
       */
      conf.setInt(Constants.KmeansClusterCount, clusters); //100
      conf.setInt(Constants.NumMapTasks, 50);
      conf.setInt(Constants.KmeansPackCount, packs); //10
      conf.setInt(Constants.KmeansClusterSteps, steps); //5
      
      FileSystem fs = FileSystem.get(conf);
      RetrievalEnvironment env = new RetrievalEnvironment(indexPath, fs);
      
      /**
       * We work with Weighted int doc vectors (It's just how we implemented our kmeans)
       * This may be a point worth examining. Try to figure out how to just use intdocvectors.
       */
      int exitCode = new BuildWeightedIntDocVectors(conf).run();

      /**
       * hardcode in the kmeans document type (if you ever change to intdocvectors, change this). 
       */
      conf.set(Constants.KmeansDocumentType, WeightedIntDocVector.class.getCanonicalName());
      

//      ArrayList<WeightedIntDocVector> testArray = new ArrayList<WeightedIntDocVector>();
      
      
      /**
       * KmeansHelper does a lot of the Kmeans work. Everything that isn't a map reduce job
       * We're going to choose K random document numbers now (getRandomDocs) for our initial
       * centroids
       */
      KmeansUtility kmeansHelper = new KmeansUtility(conf);
      kmeansHelper.getRandomDocs();
      
      /**
       * Now we actually retrieve the documents which we've chosen in getRandomDocs.
       * after this point we have our initial centroids and are ready to begin
       * clustering.
       * 
       * collectCentroids reads the centroids out of the part files from the tool and writes them
       * to one central file for distributed-caching in the cluster step
       */
      KmeansGetInitialCentroids getSomeCentroidsTool = new KmeansGetInitialCentroids(conf);
      int numInitialCentroids = getSomeCentroidsTool.run();
      LOG.info("Ran my initial Centroids Job returned: " + numInitialCentroids);
      kmeansHelper.collectCentroids();
   
      /**
       * Now we begin the clustering step. Below is the loop that will perform N cluster steps.
       * clusterThoseCentroidsTool creates K new average centroids. We then have to collect these new
       * points with collectCentroids.
       */
      for(int i=0;i<conf.getInt(Constants.KmeansClusterSteps, 5);i++){
        conf.setInt("CurrentRun", i);
        KmeansClusterOnCentroids clusterThoseCentroidsTool = new KmeansClusterOnCentroids(conf);
        int numNewCentroids = clusterThoseCentroidsTool.run();     
        kmeansHelper.collectCentroids(env.getKmeansCentroidDirectory(conf.getInt("CurrentRun", 0)));
        
        //Test code
//        testArray.clear();
////         kmeansHelper.readCurrentCentroids(testArray);
////         LOG.info("Centroids after clustering: " +testArray.length());
      }
      
      /**
       * One final step, this time we actually grab the documents and output them. 
       * After this point we are ready to start packing the clusters.
       */
      KmeansFinalClusterStep finalClusterer = new KmeansFinalClusterStep(conf);
      int resultOfFinalClusterStep = finalClusterer.run();
      

      /**
       * Beginning to pack clusters
       * 
       * clusterPackMap: a mapping from clusternumber to pack
       * docnoToClusterMap: a mapping from document number to cluster
       * 
       * bringPackMapping: fills these two maps
       * packLazyVectorsEval: creates the evaluation directories and begins sorting documents into them. unneeded?
       * 
       * PrepareEvalDirs: just used to copy random files that should be global into the evaluation directories
       *
       * writePackContents: writes the document numbers contained in each pack into a file for later filtering
       */
      HashMap<Integer,Integer> clusterPackMap = new HashMap<Integer,Integer>();
      HashMap<Integer,Integer> docnoToClusterMap = new HashMap<Integer,Integer>();  
      kmeansHelper.bringPackMapping(docnoToClusterMap, clusterPackMap); // Reads in the data from the final step and builds maps
      kmeansHelper.packLazyVectorsEval(docnoToClusterMap, clusterPackMap);
      kmeansHelper.PrepareEvalDirs(); //used to copy docnomapping over
      
      kmeansHelper.writePackMapping(clusterPackMap, docnoToClusterMap);
       
      LOG.info("About to repopulate those maps");
//      kmeansHelper.readPackMapping(clusterPackMap, docnoToClusterMap); //only needed for if you don't regenerate these everytime
      kmeansHelper.writePackContents(clusterPackMap,docnoToClusterMap);
      
      
      /**
       * Now we begin creating our evaluation indexes
       * These are located at index-trec/eval-indices/.
       * We first set runtime variables that shouldn't change if you're working on trec.
       * 
       * Then we begin looping once for each pack.
       * We basically go through the standard index creation on these evaluation indices
       * 
       * I did have to change the behavior of buildtermdocvectors for writing document lengths
       * as document numbers will no longer be contiguous.
       */
      LOG.info("Setting variables for upcoming termdocvectors");
      conf.set(Constants.CollectionName, "TREC_vol45");
      conf.set(Constants.CollectionPath,"/shared/collections/trec/trec4-5_noCRFR.xml");
      conf.set(Constants.InputFormat, TrecDocumentInputFormat.class.getCanonicalName());
      conf.set(Constants.Tokenizer,GalagoTokenizer.class.getCanonicalName());
      conf.set(Constants.DocnoMappingClass,TrecDocnoMapping.class.getCanonicalName());
      conf.setInt(Constants.DocnoOffset,0);
      
      for(int i=0;i<conf.getInt(Constants.KmeansPackCount, 10); i++){
//      for(int i=0;i<2;i++){
//        LOG.info("Working on pack 1");
        Configuration conf2 = new Configuration(conf);
        conf2.setInt("Ivory.CurrentPackNo", i);
        conf2.setInt(Constants.MinDf, 2);
        conf2.setInt(Constants.MaxDf, Integer.MAX_VALUE);
        conf2.set(Constants.IndexPath, env.getCurrentIndex(i));
        new KmeansBuildTermDocVectors(conf2).run();
//        new KmeansComputeGlobalTermStatistics(conf2).run();
        new ComputeGlobalTermStatistics(conf2).run();
//        new KmeansBuildDictionary(conf2).run();
        new BuildDictionary(conf2).run();
        new BuildIntDocVectors(conf2).run();
        new BuildIntDocVectorsForwardIndex(conf2).run();
        new BuildTermDocVectorsForwardIndex(conf2).run();
        new BuildIPInvertedIndexDocSorted(conf2).run();
        new BuildIntPostingsForwardIndex(conf2).run();
      }

      
      
    } else if (cmdline.hasOption(POSITIONAL_INDEX_LP)) {
      LOG.error("THIS IS UNSUPPORTED FOR KMEANS");
      return -1;
    }
//      LOG.info(String.format(" -%s", POSITIONAL_INDEX_LP));
//      conf.set(Constants.IndexPath, indexPath);
//      conf.setInt(Constants.NumReduceTasks, indexPartitions);
//      conf.set(Constants.PostingsListsType,
//          ivory.core.data.index.PostingsListDocSortedPositional.class.getCanonicalName());
//
//      conf.setFloat("Ivory.IndexingMapMemoryThreshold", 0.9f);
//      conf.setFloat("Ivory.IndexingReduceMemoryThreshold", 0.9f);
//      conf.setInt("Ivory.MaxHeap", 2048);
//      conf.setInt("Ivory.MaxNDocsBeforeFlush", 50000);
//
//      new BuildLPInvertedIndexDocSorted(conf).run();
//      new BuildIntPostingsForwardIndex(conf).run();
//    } else if (cmdline.hasOption(NONPOSITIONAL_INDEX_IP)) {
//      LOG.info(String.format(" -%s", NONPOSITIONAL_INDEX_IP));
//      conf.set(Constants.IndexPath, indexPath);
//      conf.setInt(Constants.NumReduceTasks, indexPartitions);
//      conf.set(Constants.PostingsListsType,
//          ivory.core.data.index.PostingsListDocSortedNonPositional.class.getCanonicalName());
//
//      new BuildIPInvertedIndexDocSorted(conf).run();
//      new BuildIntPostingsForwardIndex(conf).run();
//    } else {
//      LOG.info(String.format("Nothing to do. Specify one of the following: %s, %s, %s",
//          POSITIONAL_INDEX_IP, POSITIONAL_INDEX_LP, NONPOSITIONAL_INDEX_IP));
//    }

    return 0;
  }

  /**
   * Dispatches command-line arguments to the tool via the {@code ToolRunner}.
   */
  public static void main(String[] args) throws Exception {
    ToolRunner.run(new BuildIndexKmeans(), args);
  }
}
