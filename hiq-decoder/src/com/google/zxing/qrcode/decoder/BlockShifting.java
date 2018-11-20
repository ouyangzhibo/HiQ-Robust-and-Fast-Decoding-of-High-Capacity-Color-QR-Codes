/*
 Copyright (C) 2015 Solon Li 
 */
package com.google.zxing.qrcode.decoder;


import com.google.zxing.common.BitArray;


/**
 * In some versions, the codewords from the same data block are arranged together on the QR code. 
 * In this case, errors on local area will produce huge error on one or two data blocks, making the QR code undecodable.
 * Data-block-based error correction feature of QR code cannot help even though the percentage area of local error is smaller than correction capability.  
 * To solve this problem, this class provides a bit-based reshuffling so that codewords from the same data block will be distributed across the whole QR code in bits.   
 * @author solon li
 *
 */
public final class BlockShifting {
	/**
	 * Shuffle the bits of datablocks all over the BitMatrix
	 * @param codewords, codewords arranged under the QR code standard
	 * @param shiftingNum
	 * @param version
	 * @param ecLevel
	 * @return
	 */
	public static BitArray breakingToBit(BitArray codewords, Version version, ErrorCorrectionLevel ecLevel){
		//As the codewords are already shuffled in byte, what we really need to do it just divide the data codewords 
		//by the number of data blocks and do the shuffling
		if(codewords.getSizeInBytes() != version.getTotalCodewords()) return null; //size of codeword miss match
	    //Count the number of blocks, and the size of data codewords
		Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
	    Version.ECB[] ecBlockArray = ecBlocks.getECBlocks();
	    int numResultBlocks = 0;
	    int minNumDataByte=ecBlockArray[0].getDataCodewords(), firstLongerBlock=0;
	    int totalDataCodewords=version.getTotalCodewords()-ecBlocks.getTotalECCodewords();
	    int numEcBytes=ecBlocks.getECCodewordsPerBlock();
	    for(Version.ECB ecBlock : ecBlockArray){
	    	if(ecBlock.getDataCodewords() >minNumDataByte && firstLongerBlock==0)
				firstLongerBlock=numResultBlocks;
	    	numResultBlocks += ecBlock.getCount();
	    }	    
	    BitArray result = new BitArray();
	    //For each L data bytes, shuffle them locally on bit-by-bit bases
	    for(int i=0,L=numResultBlocks;i<minNumDataByte;i++){
	    	for(int j=0;j<8;j++){	    		
	    		for(int k=0;k<L;k++){
	    			result.appendBit( codewords.get( ((i*L)+k)*8 +j ) );
	    		}	    			
	    	}
	    }
	    //Shuffle bytes from longer data blocks
	    if(firstLongerBlock >0){
		    for(int j=0, offset=minNumDataByte*numResultBlocks;j<8;j++){	    		
	    		for(int k=0,L=numResultBlocks-firstLongerBlock;k<L;k++){
	    			result.appendBit( codewords.get((offset+k)*8 +j) );
	    		}	    			
	    	}
	    }
	    if(result.getSizeInBytes() != totalDataCodewords) return null; //size miss match
	    //Shuffle bytes on the error correction blocks
	    for(int i=0,L=numResultBlocks;i<numEcBytes;i++){
	    	for(int j=0;j<8;j++){	    		
	    		for(int k=0;k<L;k++){
	    			result.appendBit( codewords.get((totalDataCodewords+(i*L)+k)*8 +j) );
	    		}	    			
	    	}
	    }
	    if(result.getSizeInBytes() != codewords.getSizeInBytes()) return null; //size miss match
	    return result;	
	}
	/**
	 * Grouping the bits shuffled by breakingToBit back to codewords under the QR code standard
	 * @param codewords
	 * @param shiftingNum
	 * @param version
	 * @param ecLevel
	 * @return
	 */
	public static byte[] groupingToByte(byte[] codewords, Version version, ErrorCorrectionLevel ecLevel){
		if(codewords.length != version.getTotalCodewords()) return null; //size of codeword miss match
	    //Count the number of blocks, and the size of data codewords
		Version.ECBlocks ecBlocks = version.getECBlocksForLevel(ecLevel);
	    Version.ECB[] ecBlockArray = ecBlocks.getECBlocks();
	    int numResultBlocks = 0;
	    int minNumDataByte=ecBlockArray[0].getDataCodewords(), firstLongerBlock=0;
	    int totalDataCodewords=version.getTotalCodewords()-ecBlocks.getTotalECCodewords();
	    int numEcBytes=ecBlocks.getECCodewordsPerBlock();
	    for(Version.ECB ecBlock : ecBlockArray){
	    	if(ecBlock.getDataCodewords() >minNumDataByte && firstLongerBlock==0)
				firstLongerBlock=numResultBlocks;
	    	numResultBlocks += ecBlock.getCount();
	    }
	    BitArray result = new BitArray();
	    //For each L data bytes, group the bits locally back into bytes	    
	    for(int i=0,L=numResultBlocks;i<minNumDataByte;i++){
	    	int offset=i*L;
	    	for(int k=0;k<L;k++){	    		
	    		for(int j=0;j<8;j++){
	    			int Boffset=L*j+k;	    			
	    			int mask=1<<(7-(Boffset%8));
	    			result.appendBit( ( (codewords[offset+Boffset/8] & mask) >0)? true:false);
	    		}	    			
	    	}
	    }
	    //Shuffle bytes from longer data blocks
	    if(firstLongerBlock >0){
		    for(int k=0,L=numResultBlocks-firstLongerBlock, offset=minNumDataByte*numResultBlocks;k<L;k++){
		    	for(int j=0;j<8;j++){	    		
		    		int Boffset=L*j+k;	    			
	    			int mask=1<<(7-(Boffset%8));
	    			result.appendBit( ( (codewords[offset+Boffset/8] & mask) >0)? true:false);
	    		}	    			
	    	}
	    }
	    if(result.getSizeInBytes() != totalDataCodewords) return null; //size miss match
	    //Shuffle bytes on the error correction blocks
	    for(int i=0,L=numResultBlocks,limit=codewords.length;i<numEcBytes;i++){
	    	int offset=i*L+totalDataCodewords;
	    	for(int k=0;k<L;k++){	    		
	    		for(int j=0;j<8;j++){
	    			int Boffset=L*j+k;	    			
	    			int mask=1<<(7-(Boffset%8));
	    			Boffset=offset+(Boffset/8);
	    			if(Boffset<limit)
	    				result.appendBit( ( (codewords[Boffset] & mask) >0)? true:false);
	    		}	    			
	    	}
	    }
	    if(result.getSizeInBytes() != codewords.length) return null; //size miss match
	    byte[] returnValue = new byte[result.getSizeInBytes()];
		result.toBytes(0, returnValue, 0, returnValue.length);
		return returnValue;
	}
}