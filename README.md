Ivory
=====

A Hadoop toolkit for web-scale information retrieval research:
http://ivory.cc


Kmeans Code Branch

===
   
This branch offers an initial implementation of the Trec-45 eval experiments found [here](http://lintool.github.io/Ivory/docs/exp-trec45.html)

To this point this implementation should be runnable but is returning evaluation results that are lower than expected. Below is both an explanation of how to make and run the code, as well as some thoughts on why the code is underperforming.

Running the code
---

Making the code: You should be able to make this code by just pulling the repository and running

	ant clean; ant
	
In order to run the code we must first pre-process the dataset.

	etc/hadoop-cluster.sh ivory.app.PreprocessTrec45 \
	  -collection /shared/collections/trec/trec4-5_noCRFR.xml -index index-trec
	  
Then we can run the clustering process:

	etc/hadoop-cluster.sh ivory.kmeans.app.BuildIndexKmeans \ 
	 -index index-trec -indexPartitions 1 -positionalIndexIP 
	 
	etc/hadoop-cluster.sh ivory.kmeans.app.BuildIndexKmeans \
	-numPacks 5 -numClusters 10 -numSteps 5 -index index-trec -indexPartitions 1 \
	-positionalIndexIP 
	
This will result in an evaluation directory under index called "eval-indices". This must be retrieved from hdfs for the evaluation step.

Now we run the evaluation step:

	./run.sh ivory.kmeans.app.KmeansRunQueryLocal <full path to eval-indices> \
	  data/trec/run.robust04.jon.xml data/trec/queries.robust04.xml
	  
Finally, we can run trec_eval on our output:

	etc/trec_eval data/trec/qrels.robust04.noCRFR.txt ranking.robust04-bm25-base.txt
	 
This results in worse than expected output. 

Discussion
-

1)	One obvious explanation for why this may not be working is that there is some error in the existing code. Perhaps we're losing some documents somewhere? Possible locations include in KmeansHelper when we read in the results of the final cluster. This is "Bring pack mapping". It could be in the kMeansBuildTermDocVectors filtering step?

2) Another possiblity is if scoring is relative to all documents within a pack then it might be possible that that one pack might have the best results for a single query. The other packs will have worse documents with similar scores due to the scoring happening at a pack level. Not sure of this possibility. I think it's likely the first possibility.