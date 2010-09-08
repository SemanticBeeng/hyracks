/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.storage.am.btree.impls;

import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.storage.am.btree.api.IFieldAccessor;

public class MultiComparator {
    	
	private static final long serialVersionUID = 1L;
	
	private IBinaryComparator[] cmps = null;
	private IFieldAccessor[] fields = null;
	
	public MultiComparator(IBinaryComparator[] cmps, IFieldAccessor[] fields) {
		this.cmps = cmps;
		this.fields = fields;
	}
	
	public IBinaryComparator[] getComparators() {
	    return cmps;
	}
	
	public int getKeyLength() {
	    return cmps.length;
	}
	
	public void setComparators(IBinaryComparator[] cmps) {
		this.cmps = cmps;
	}
	
	public int size() {
		return cmps.length;
	}
	
	public void setFields(IFieldAccessor[] fields) {
		this.fields = fields;
	}	
	
	public IFieldAccessor[] getFields() {
		return fields;
	}
		
	public int compare(byte[] dataA, int recOffA, byte[] dataB, int recOffB) {
		int lenA;
		int lenB;
		for(int i = 0; i < cmps.length; i++) {
			lenA = fields[i].getLength(dataA, recOffA);
			lenB = fields[i].getLength(dataB, recOffB);			
			int cmp = cmps[i].compare(dataA, recOffA, lenA, dataB, recOffB, lenB);	
    		if(cmp < 0) return -1;
    		else if(cmp > 0) return 1;
    		recOffA += lenA; 
   			recOffB += lenB;    			
    	}
    	return 0;
	}
	
	public int fieldRangeCompare(byte[] dataA, int recOffA, byte[] dataB, int recOffB, int startFieldIndex, int numFields) {
		int lenA;
		int lenB;
		for(int i = startFieldIndex; i < startFieldIndex + numFields; i++) {
			lenA = fields[i].getLength(dataA, recOffA);
			lenB = fields[i].getLength(dataB, recOffB);			
			int cmp = cmps[i].compare(dataA, recOffA, lenA, dataB, recOffB, lenB);
    		if(cmp < 0) return -1;
    		else if(cmp > 0) return 1;
    		recOffA += lenA; 
   			recOffB += lenB;
    	}
    	return 0;
	}
	
	public int getRecordSize(byte[] data, int recOff) {
		int runner = recOff;
		for(int i = 0; i < fields.length; i++) {
			runner += fields[i].getLength(data, runner);
		}
		return runner - recOff;
	}
	
	public int getKeySize(byte[] data, int recOff) {
		int runner = recOff;
		for(int i = 0; i < cmps.length; i++) {
			runner += fields[i].getLength(data, runner);
		}
		return runner - recOff;
	}		
	
	public String printRecord(byte[] data, int recOff) {
		StringBuilder strBuilder = new StringBuilder();
	    int runner = recOff;
		for(int i = 0; i < fields.length; i++) {
			strBuilder.append(fields[i].print(data, runner) + " ");
			runner += fields[i].getLength(data, runner);
		}
		return strBuilder.toString();
	}
	
	public String printKey(byte[] data, int recOff) {
		StringBuilder strBuilder = new StringBuilder();		
		int runner = recOff;
		for(int i = 0; i < cmps.length; i++) {
			strBuilder.append(fields[i].print(data, runner) + " ");			
			runner += fields[i].getLength(data, runner);
		}
		return strBuilder.toString();
	}		
}