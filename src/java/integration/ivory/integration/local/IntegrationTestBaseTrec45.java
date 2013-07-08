package ivory.integration.local;

import static org.junit.Assert.assertTrue;
import ivory.app.PreprocessCollection;
import ivory.core.eval.Qrels;
import ivory.integration.IntegrationUtils;
import ivory.regression.basic.Robust04_Basic;
import ivory.smrf.retrieval.BatchQueryRunner;

import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class IntegrationTestBaseTrec45 {
  private static final Logger LOG = Logger.getLogger(IntegrationTestBaseTrec45.class);
  private static final Path collectionPath = new Path("/scratch0/collections/trec/trec4-5_noCRFR.xml");

  public void runBuildIndex(String index, String[] args) throws Exception {
    Configuration conf = new Configuration();
    FileSystem fs = FileSystem.getLocal(conf);

    assertTrue(fs.exists(collectionPath));

    fs.delete(new Path(index), true);

    List<String> jars = Lists.newArrayList();
    jars.add(IntegrationUtils.getJar("lib", "cloud9"));
    jars.add(IntegrationUtils.getJar("lib", "guava"));
    jars.add(IntegrationUtils.getJar("lib", "dsiutils"));
    jars.add(IntegrationUtils.getJar("lib", "fastutil"));
    jars.add(IntegrationUtils.getJar("lib", "jsap"));
    jars.add(IntegrationUtils.getJar("lib", "sux4j"));
    jars.add(IntegrationUtils.getJar("lib", "commons-collections"));
    jars.add(IntegrationUtils.getJar("lib", "kamikaze"));
    jars.add(IntegrationUtils.getJar("dist", "ivory"));

    String libjars = String.format("-libjars=%s", Joiner.on(",").join(jars));

    String[] cmdArgs = new String[] { "hadoop jar",
        IntegrationUtils.getJar("dist", "ivory"),
        ivory.app.PreprocessTrec45.class.getCanonicalName(),
        IntegrationUtils.LOCAL_ARGS, libjars,
        "-" + PreprocessCollection.COLLECTION_PATH, collectionPath.toString(),
        "-" + PreprocessCollection.INDEX_PATH, index };

    IntegrationUtils.exec(Joiner.on(" ").join(cmdArgs));

    cmdArgs = (String[]) ArrayUtils.addAll(new String[] { 
        "hadoop jar", IntegrationUtils.getJar("dist", "ivory"),
        ivory.app.BuildIndex.class.getCanonicalName(), IntegrationUtils.LOCAL_ARGS, libjars }, args);

    IntegrationUtils.exec(Joiner.on(" ").join(cmdArgs));

    // Done with indexing, now do retrieval run.
    String[] params = new String[] {
        "data/trec/run.robust04.basic.xml",
        "data/trec/queries.robust04.xml" };

    BatchQueryRunner qr = new BatchQueryRunner(params, fs, index);

    long start = System.currentTimeMillis();
    qr.runQueries();
    long end = System.currentTimeMillis();

    LOG.info("Total query time: " + (end - start) + "ms");

    Robust04_Basic.verifyAllResults(qr.getModels(), qr.getAllResults(), qr.getDocnoMapping(),
        new Qrels("data/trec/qrels.robust04.noCRFR.txt"));

    LOG.info("Done!");
  }
}
