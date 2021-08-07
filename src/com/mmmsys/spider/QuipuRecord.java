package com.mmmsys.spider;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

public class QuipuRecord {

    public SocketAddress clientAddress;
    public ByteBuffer buffer = ByteBuffer.allocate(4096);
    
    public QuipuRecord(SocketAddress devAddre, ByteBuffer data){
    	
    	this.buffer = ByteBuffer.allocate(data.limit());
    	data.rewind();//copy from the beginning
       	buffer.put(data);
       	data.rewind();
       	buffer.flip();
    	this.clientAddress = devAddre;
    
	}
    
    public SocketAddress getQuipuClient(){
    	return clientAddress;
    	
    }
    public ByteBuffer getClientData(){
    	return this.buffer;
    	
    }
}
