package com.mmmsys.spider;


import static java.lang.Math.toIntExact;

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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.charset.StandardCharsets;

import com.mmmsys.spider.TCB.TCBStatus;

public class QuipuTCPInputNet implements Runnable
{
    private static final String TAG = QuipuTCPInputNet.class.getSimpleName();
    private static final int HEADER_SIZE = PacketInfo.IP4_HEADER_SIZE + PacketInfo.TCP_HEADER_SIZE;

    private ConcurrentLinkedQueue<QuipuRecord> outputQueue;
    private Selector selector;
    //private DatagramSocket proxysock;
    private SocketChannel proxysock;
   /*
    public M3TCPInput(ConcurrentLinkedQueue<ByteBuffer> outputQueue, DatagramChannel sock, Selector selector)
    {
        this.outputQueue = outputQueue;
        this.selector = selector;
        this.proxysock = sock;
    }*/

    public QuipuTCPInputNet(ConcurrentLinkedQueue<QuipuRecord> networkToDeviceQueue, 
			Selector tcpSelector) {
		// TODO Auto-generated constructor stub
        this.outputQueue = networkToDeviceQueue;
        this.selector = tcpSelector;
        //this.tpFactory = new TCPPacketFactory();
       // this.proxysock = proxysock2;

	}

	@Override
    public void run()
    {
        System.out.println(TAG + " Started");
        try
        {
        
        	while(!Thread.interrupted()){
            
                int readyChannels = selector.select();

                if (readyChannels == 0) {
                    Thread.sleep(100);
                    continue;
                }

                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = keys.iterator();

                while (keyIterator.hasNext() && !Thread.interrupted())
                {
                    SelectionKey key = keyIterator.next();
                    if (key.isValid())
                    {
                        if (key.isConnectable())
                            processConnect(key, keyIterator);
                        else if (key.isReadable())
                            processInput(key, keyIterator);
                    }
                }
            }
        }
        
        catch (InterruptedException e)
        {
           System.out.println(TAG + "Stopping");
	    // Log.i(TAG, "Stopping");
        }
        catch (IOException e)
        {
           System.out.println(TAG + e.toString()); 
	   //Log.w(TAG, e.toString(), e);
        }
        
    }

    private void processConnect(SelectionKey key, Iterator<SelectionKey> keyIterator)
    {
    
    }

    private void processInput(SelectionKey key, Iterator<SelectionKey> keyIterator)
    {
       
    }

    
}
