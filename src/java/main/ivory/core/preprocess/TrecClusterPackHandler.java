package ivory.core.preprocess;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.io.Text;
import org.apache.log4j.Logger;
import org.mortbay.log.Log;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import edu.umd.cloud9.collection.DocnoMapping;

public class TrecClusterPackHandler extends DefaultHandler {
  private static final Logger sLogger = Logger.getLogger(TrecClusterPackHandler.class);
  boolean inDocId = false;
  String toOutput = new String();
  HashMap<Integer,Integer>cpm;
  HashMap<Integer,Integer>dtcm;
  DocnoMapping docMapping;
  Integer where;
  ArrayList<FSDataOutputStream> outStreams;

  public TrecClusterPackHandler(){
    super();
  }

  public TrecClusterPackHandler(HashMap<Integer,Integer> clusterPackMap,  HashMap<Integer,Integer> docnoToClusterMap,
      DocnoMapping dmap, ArrayList<FSDataOutputStream>outStreams, DocnoMapping docMap){
    super();
    this.cpm = clusterPackMap;
    this.dtcm = docnoToClusterMap;
    this.outStreams = outStreams;
    this.docMapping = docMap;
  }

  public void setMaps( HashMap<Integer,Integer> clusterPackMap,  HashMap<Integer,Integer> docnoToClusterMap){
    this.cpm = clusterPackMap;
    this.dtcm = docnoToClusterMap;
  }


  public void startElement(String uri, String localName,String qName, 
      Attributes attributes) throws SAXException {

    if(qName.equalsIgnoreCase("begin")){
      sLogger.info("Beginning XML FILE");
    }else{
      toOutput.concat("<"+qName+">\n");
      if (qName.equalsIgnoreCase("DOCNO")) {
        inDocId = true;
      }
    }
  }

  public void endElement(String uri, String localName,
      String qName) throws SAXException {
    //done with one document
    //output to correct file
    //clear string
    if(qName.equalsIgnoreCase("BEGIN")){
      sLogger.info("Ending XML FIle");
    }else{
      toOutput.concat("</"+qName+">");
      if(qName.equalsIgnoreCase("DOC")){
        Text output = new Text(this.toOutput);
        try {
          sLogger.info(" Which index? : "+this.where);
          if(this.where==null){
            sLogger.info("Dumping output: "+output);
            output = null;
            toOutput = "";
          }else{
            sLogger.info(" this one is?: " + this.outStreams.get(this.where));
//            output.write(this.outStreams.get(this.where));
            this.outStreams.get(this.where).write(this.toOutput.getBytes());
          }
        } catch (IOException e) {
          sLogger.info("Failed to write the outputstream: "+this.outStreams.get(this.where));
          e.printStackTrace();
        }
        output = null;
        toOutput="";
      }
    }
  }

  public void characters(char ch[], int start, int length) throws SAXException {
    if(inDocId){
      String lookup = new String(ch,start,length);
      sLogger.info("Recieved Characters: " + lookup);
      sLogger.info("this.docMapping: " + this.docMapping);
      this.where = this.cpm.get(this.dtcm.get(this.docMapping.getDocno(lookup)));
      //do the work of lookup here
      toOutput.concat(lookup+"\n");
      this.inDocId = false;
    }else{
      toOutput.concat(new String(ch,start,length)+"\n");
    }   
  }

}
