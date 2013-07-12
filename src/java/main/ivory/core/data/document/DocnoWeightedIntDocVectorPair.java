package ivory.core.data.document;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import ivory.core.data.document.WeightedIntDocVector;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Writable;

public class DocnoWeightedIntDocVectorPair implements Writable {

  WeightedIntDocVector vector;
  IntWritable docno;
  
  
  public DocnoWeightedIntDocVectorPair(){
    vector = new WeightedIntDocVector();
    docno = new IntWritable();
  }
  
  public DocnoWeightedIntDocVectorPair(IntWritable no, WeightedIntDocVector doc){
    vector = doc;
    docno = no;
  }
  
  public void setDoc(WeightedIntDocVector doc){
    this.vector = doc;
  }
  
  public void setDocno(IntWritable no){
    this.docno = no;
  }
  
  public IntWritable getDocno(){
    return this.docno;
  }
  
  public WeightedIntDocVector getVector(){
    return this.vector;
  }
  
  public void readFields(DataInput input) throws IOException {
    // TODO Auto-generated method stub
    docno.readFields(input);
    vector.readFields(input);
    
  }

  public void write(DataOutput output) throws IOException {
    // TODO Auto-generated method stub
    docno.write(output);
    vector.write(output);
  }

}
