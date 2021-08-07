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

import com.att.arotcpcollector.tcp.TCPHeader;
import com.att.arotcpcollector.tcp.TCPPacketFactory;
import com.attn.protocol.Packet;
import com.mmmsys.spider.TCB.TCBStatus;

public class QuipuTCPInputNet implements Runnable
{
    private static final String TAG = QuipuTCPInputNet.class.getSimpleName();
    private static final int HEADER_SIZE = PacketInfo.IP4_HEADER_SIZE + PacketInfo.TCP_HEADER_SIZE;

    private ConcurrentLinkedQueue<QuipuRecord> outputQueue;
    private Selector selector;
    //private DatagramSocket proxysock;
    private SocketChannel proxysock;
    private TCPPacketFactory tpFactory;
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
        this.tpFactory = new TCPPacketFactory();
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
        TCB tcb = (TCB) key.attachment();
        Packet referencePacket = tcb.templatePack;
        SocketAddress clientAddress = tcb.clientAddress;
        //System.out.println(TAG+" reference packet "+referencePacket.toString());
        try
        {
            if (tcb.channel.finishConnect())
            {
                keyIterator.remove();
                tcb.status = TCBStatus.SYN_RECEIVED;

                ByteBuffer responseBuffer = ByteBufferPool.acquire();
                // TODO: Set MSS for receiving larger packets from the device
                
               
                
                
                TCPHeader quitcpHeader = referencePacket.getTCPHeader();
                
                //quitcpHeader.setSequenceNumber(tcb.mySequenceNum);
                //quitcpHeader.setAckNumber(tcb.myAcknowledgementNum);
                
                quitcpHeader.setSequenceNumber(tcb.myAcknowledgementNum);
                quitcpHeader.setAckNumber(tcb.mySequenceNum);
                
                byte [] inpsynAck = this.tpFactory.createSynAckPacketData(referencePacket.getIPHeader(),quitcpHeader).getBuffer();
        		
                responseBuffer.put(inpsynAck).limit(inpsynAck.length);
        		 
                outputQueue.offer(new QuipuRecord(clientAddress,responseBuffer));
                
                
                //ByteBuffer tempPack = ByteBuffer.allocateDirect(4096);
                //byte [] inpsyn= referencePacket.getBuffer();
                //tempPack.put(inpsyn);
                
                //System.out.println(TAG+" SYN seqeunce and acks numbers "+tempPack.toString());	
                //tempPack.rewind();
                System.out.println(TAG+" SYN seqeunce and acks numbers "+ tcb.myAcknowledgementNum+"   "+tcb.mySequenceNum);	
                System.out.println(TAG+" SYNACK seqeunce and acks numbers "+new PacketInfo(responseBuffer).toString());	
                tcb.mySequenceNum++; // SYN counts as a byte
                key.interestOps(SelectionKey.OP_READ);
                responseBuffer.clear();
            }
        }
        catch (IOException e)
        {
            //Log.e(TAG, "Connection error: " + tcb.ipAndPort, e);
            ByteBuffer responseBuffer = ByteBufferPool.acquire();
            
            //referencePacket.updateTCPBuffer(responseBuffer, (byte) PacketInfo.TCPHeader.RST, 0, tcb.myAcknowledgementNum, 0, 0);
            TCPHeader hdr = referencePacket.getTCPHeader();
            hdr.setAckNumber(tcb.myAcknowledgementNum);
            byte [] rstBytes = this.tpFactory.createRstData(referencePacket.getIPHeader(), hdr,0);
            responseBuffer.put(rstBytes).limit(rstBytes.length);
            System.out.println(TAG + "RST  "+responseBuffer.toString());
            //responseBuffer.limit(HEADER_SIZE);
  
            responseBuffer.rewind();
            //outputQueue.offer(responseBuffer);
            outputQueue.offer(new QuipuRecord(clientAddress,responseBuffer));
            TCB.closeTCB(tcb);
            responseBuffer.clear();
        }
    }

    private void processInput(SelectionKey key, Iterator<SelectionKey> keyIterator)
    {
        keyIterator.remove();
        ByteBuffer receiveBuffer = ByteBufferPool.acquire();
        // Leave space for the header
        //receiveBuffer.position(HEADER_SIZE);
        SocketAddress clientAddress  = null;
        TCB tcb = (TCB) key.attachment();
        synchronized (tcb)
        {
            Packet referencePacket = tcb.templatePack;
            
            clientAddress = tcb.clientAddress;
            SocketChannel inputChannel = (SocketChannel) key.channel();
            int readBytes;
            try
            {
                readBytes = inputChannel.read(receiveBuffer);
            }
            catch (IOException e)
            {
                System.out.println(TAG+ "Network read error: " + tcb.ipAndPort+ e.toString());
                //int localTime = toIntExact(System.currentTimeMillis() - tcb.mytime);
                TCPHeader hdr = referencePacket.getTCPHeader();
                hdr.setAckNumber(tcb.myAcknowledgementNum);
                byte [] rstBytes = this.tpFactory.createRstData(referencePacket.getIPHeader(), hdr,0);
                receiveBuffer.put(rstBytes).limit(rstBytes.length);
                //referencePacket.updateTCPBuffer(receiveBuffer, (byte) PacketInfo.TCPHeader.RST, 0, tcb.myAcknowledgementNum, 0, 0);
                //receiveBuffer.limit(HEADER_SIZE);
            	System.out.println(TAG + "RST  "+receiveBuffer.toString());
                
                receiveBuffer.rewind();
            	outputQueue.offer(new QuipuRecord(clientAddress,receiveBuffer));
                TCB.closeTCB(tcb);
                return;
            }

            if (readBytes == -1)
            {
                // End of stream, stop waiting until we push more data
                key.interestOps(0);
                tcb.waitingForNetworkData = false;

                if (tcb.status != TCBStatus.CLOSE_WAIT)
                {
                    ByteBufferPool.release(receiveBuffer);
                    return;
                }

                tcb.status = TCBStatus.LAST_ACK;
                TCPHeader hdr = referencePacket.getTCPHeader();
                hdr.setAckNumber(tcb.myAcknowledgementNum);
                hdr.setSequenceNumber(tcb.mySequenceNum);
                byte [] finBytes = this.tpFactory.createFinData(referencePacket.getIPHeader(), hdr, tcb.myAcknowledgementNum, tcb.mySequenceNum, 0, 0);
                //int localTime = toIntExact(System.currentTimeMillis() - tcb.mytime);
                //referencePacket.updateTCPBuffer(receiveBuffer, (byte) PacketInfo.TCPHeader.FIN, tcb.mySequenceNum, tcb.myAcknowledgementNum, localTime,0);
                tcb.mySequenceNum++; // FIN counts as a byte
                
                //receiveBuffer.limit(HEADER_SIZE);
                receiveBuffer.put(finBytes).limit(finBytes.length);
                //receiveBuffer.limit(HEADER_SIZE+referencePacket.tcpHeader.optionsAndPadding.length);             
            	System.out.println(TAG + "FIN  "+ receiveBuffer.toString());

                receiveBuffer.rewind();
                outputQueue.offer(new QuipuRecord(clientAddress,receiveBuffer));
            }
            if(readBytes>0)
            {
                // XXX: We should ideally be splitting segments by MTU/MSS, but this seems to work without
             	System.out.println(TAG+ "Server Response " + readBytes+" bytes");
             	
             	byte [] payload = new byte[readBytes];
             	receiveBuffer.get(payload);
             	TCPHeader hdr = referencePacket.getTCPHeader();
             	hdr.setAckNumber(tcb.myAcknowledgementNum);
             	hdr.setSequenceNumber(tcb.mySequenceNum);
             	byte [] dataAckBytes = this.tpFactory.createResponsePacketData(referencePacket.getIPHeader(), hdr, payload, true, tcb.myAcknowledgementNum, tcb.mySequenceNum, 0, 0);
             	receiveBuffer.put(dataAckBytes).limit(dataAckBytes.length);
             	
             	
             	
             	//referencePacket.updateTCPBuffer(receiveBuffer, (byte) (PacketInfo.TCPHeader.PSH | PacketInfo.TCPHeader.ACK),tcb.mySequenceNum, tcb.myAcknowledgementNum, 0, readBytes);
                tcb.mySequenceNum += readBytes; // Next sequence number
                System.out.println(TAG + "PSH  ACK "+receiveBuffer.toString());

                
                //receiveBuffer.limit(HEADER_SIZE + readBytes);
                
                
                //receiveBuffer.limit(HEADER_SIZE+readBytes+referencePacket.tcpHeader.optionsAndPadding.length); 
                receiveBuffer.rewind();
                outputQueue.offer(new QuipuRecord(clientAddress,receiveBuffer));
            }
	    //outputQueue.offer(new QuipuRecord(clientAddress,receiveBuffer));

        }
        ByteBufferPool.release(receiveBuffer);
       // outputQueue.offer(new QuipuRecord(clientAddress,receiveBuffer));
    }

    
}
