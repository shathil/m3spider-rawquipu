package com.mmmsys.spider;

/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/


import java.io.IOException;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class QuipuUDPInput implements Runnable
{
    private static final String TAG = QuipuUDPInput.class.getSimpleName();
    private static final int HEADER_SIZE = PacketInfo.IP4_HEADER_SIZE + PacketInfo.UDP_HEADER_SIZE;

    private Selector selector;
    //private DatagramSocket proxysoc;
    private SocketChannel proxysoc;
    private ConcurrentLinkedQueue<QuipuRecord> outputQueue;

    public QuipuUDPInput(Selector udpSelector,
            ConcurrentLinkedQueue<QuipuRecord> networkToDeviceQueue) {
    // TODO Auto-generated constructor stub
    	this.outputQueue = networkToDeviceQueue;
//this.proxysoc = proxysock;
    	this.selector = udpSelector;

    }

@Override
public void run()
{
	try
	{
		System.out.println(TAG + " Started");
		while (!Thread.interrupted())
		{
	    int readyChannels = selector.select();
	
	    if (readyChannels == 0) {
	        Thread.sleep(10);
	        continue;
	    }
	
	    Set<SelectionKey> keys = selector.selectedKeys();
	    Iterator<SelectionKey> keyIterator = keys.iterator();
	
	    while (keyIterator.hasNext() && !Thread.interrupted())
	    {
	        SelectionKey key = keyIterator.next();
	        if (key.isValid() && key.isReadable())
	        {
	            keyIterator.remove();
	
	            ByteBuffer receiveBuffer = ByteBufferPool.acquire();
	            // Leave space for the header
	            receiveBuffer.position(HEADER_SIZE);
	
	            DatagramChannel inputChannel = (DatagramChannel) key.channel();
	            // but that probably won't happen with UDP
	            int readBytes = inputChannel.read(receiveBuffer);
	
	            if(readBytes > 0){
	
	            	    QuipuRecord qReccord = (QuipuRecord) key.attachment(); // this works
	            	    qReccord.buffer.position(0);
	            	    PacketInfo referencePacket = new PacketInfo(qReccord.buffer); // this is we need to allocate
	            	    referencePacket.swapSourceAndDestination();
	                    referencePacket.updateUDPBuffer(receiveBuffer, readBytes);

	                    receiveBuffer.rewind();
			    receiveBuffer.limit(HEADER_SIZE + readBytes);
	                    outputQueue.offer(new QuipuRecord(qReccord.clientAddress,receiveBuffer));
	
	            }
	            ByteBufferPool.release(receiveBuffer);
	        }
	    }
		
		}
}
catch (InterruptedException e)
{
//Log.i(TAG, "Stopping");
}
catch (IOException e)
{
//Log.w(TAG, e.toString(), e);
}
}
}
