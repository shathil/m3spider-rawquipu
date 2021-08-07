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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.charset.StandardCharsets;

//import com.mmmsys.spider.PacketInfo.TCPHeader;
import com.att.arotcpcollector.ip.IPHeader;
import com.att.arotcpcollector.ip.IPPacketFactory;
import com.att.arotcpcollector.ip.IPv4Header;
import com.att.arotcpcollector.tcp.PacketHeaderException;
import com.att.arotcpcollector.tcp.TCPHeader;
import com.att.arotcpcollector.tcp.TCPPacketFactory;
import com.attn.protocol.Packet;
import com.mmmsys.spider.PacketInfo.BitUtils;
import com.mmmsys.spider.TCB.TCBStatus;
import static java.lang.Math.toIntExact;

public class QuipuTCPOutputNet implements Runnable
{
    private static final String TAG = QuipuTCPOutputNet.class.getSimpleName();

    /*replace vpn service with a new server socket*/
    //private LocalVPNService vpnService;
    private ConcurrentLinkedQueue<QuipuRecord> inputQueue;
    private ConcurrentLinkedQueue<QuipuRecord> outputQueue;
    private Selector selector;

    private static int HEADER_SIZE = PacketInfo.IP4_HEADER_SIZE + PacketInfo.TCP_HEADER_SIZE ; 
    private SocketAddress clientAddress;
    
    private Random random = new Random();
    private long timerStarts;
    private TCPPacketFactory tpFactory; 
    
    public QuipuTCPOutputNet(ConcurrentLinkedQueue<QuipuRecord> inputQueue, ConcurrentLinkedQueue<QuipuRecord> outputQueue,
                     Selector selector)
    {
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.selector = selector;
        this.clientAddress = null;
        this.tpFactory = new TCPPacketFactory();
       // this.vpnService = vpnService;
    }

    @Override
    public void run()
    {
        System.out.println(TAG + " Started");
        timerStarts = System.currentTimeMillis();
        
        
        try
        {

            Thread currentThread = Thread.currentThread();
            while (!Thread.interrupted())
            {   //System.out.println(TAG+ "I am here");
                //PacketInfo currentPacket = null;
                QuipuRecord currentQuipu;
                // TODO: Block when not connected
                do
                {
                    currentQuipu = inputQueue.poll();
                    if (currentQuipu != null){
                    	//System.out.println(TAG+" "+currentPacket.toString());
                        break;
                    }
                } while (!currentThread.isInterrupted());

                if (currentThread.isInterrupted()){
                	
                	System.out.println();
                	
                	break;
                }

                
                IPv4Header ipHeader = null;
                TCPHeader tcpHeader=null;
                byte [] buffer = currentQuipu.buffer.array();
                byte version = (byte) (buffer[0] >> 4);

        		if (version == 4) {
        			ipHeader= (IPv4Header) IPPacketFactory.createIPv4Header(version, buffer,0);
        		} 
        		
        		
        		tcpHeader = tpFactory.createTCPHeader(buffer, ipHeader.getIPHeaderLength());
                
                ByteBuffer responseBuffer = ByteBuffer.allocate(60);
                InetAddress destinationAddress = ipHeader.getDestinationIP();               
                int destinationPort = tcpHeader.getDestinationPort();
                int sourcePort = tcpHeader.getSourcePort();
                String ipAndPort = destinationAddress.getHostAddress() + ":" +
                        destinationPort + ":" + sourcePort;
                
                this.clientAddress = currentQuipu.clientAddress;
                
                TCB tcb = TCB.getTCB(ipAndPort);
                if (tcb == null){
                	
                	Packet refPack = new Packet();
                	refPack.setIPHeader(ipHeader);
                	refPack.setTCPheader(tcpHeader);
                	initializeConnection(ipAndPort, destinationAddress, destinationPort,
                            refPack, tcpHeader, responseBuffer);
                    //System.out.println(TAG+ responseBuffer.toString());
                 }
 
                 else if (tcpHeader.isSYN()){
                	 
                    processDuplicateSYN(tcb, tcpHeader, responseBuffer);
                    //System.out.println("duplicate eSyn"+ responseBuffer.toString());
                 }
                 else if (tcpHeader.isRST()){
                //	System.out.println("iRST"+ responseBuffer.toString());
                    closeCleanly(tcb, responseBuffer);
                 }
                 else if (tcpHeader.isFIN()){
                	//System.out.println("isFin"+ responseBuffer.toString());
                    processFIN(tcb, tcpHeader, responseBuffer);}
                 else if (tcpHeader.isACK()){
                	int payloadSize = ipHeader.getTotalLength()-ipHeader.getIPHeaderLength()-tcpHeader.getTCPHeaderLength();
                	ByteBuffer payloadBuffer = ByteBuffer.allocate(payloadSize);
                	
                	//Put (byte[] src, int offset, int length);
                	payloadBuffer.put(buffer, ipHeader.getIPHeaderLength()+tcpHeader.getTCPHeaderLength(), buffer.length);
                	
                	payloadBuffer.rewind();
                	
                    processACK(tcb, tcpHeader, payloadBuffer, responseBuffer);
                }

                // XXX: cleanup later
                if (responseBuffer.position() == 0)
                    ByteBufferPool.release(responseBuffer);
                
                //ByteBufferPool.release(payloadBuffer);
            }
        }
        catch (IOException e)
        {
            System.out.println(TAG + e.toString() + e.toString());
        } catch (PacketHeaderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        finally
        {
            TCB.closeAll();
        }
    }
    
    
   

    private void initializeConnection(String ipAndPort, InetAddress destinationAddress, int destinationPort,
                                      Packet qPack, TCPHeader tcpHeader, ByteBuffer responseBuffer)
            throws IOException
    {
        
    	
    	
    	//currentPacket.swapSourceAndDestination();
    	
    	
    	
    	
        if (tcpHeader.isSYN())
        { 
        	
        	//SocketAddress clientAddress = currentPacket.getClientAddress();
            //System.out.println(TAG+"Syn received ");
            SocketChannel outputChannel = SocketChannel.open();
            outputChannel.configureBlocking(false);
            outputChannel.socket().setTcpNoDelay(true);
            //outputChannel.socket().setSendBufferSize(65535);
            //outputChannel.socket().setReceiveBufferSize(65535);
            //outputChannel.socket().setSoTimeout(0);
            outputChannel.socket().setKeepAlive(true);

            //vpnService.protect(outputChannel.socket());

            /// TCB also contains the original source address
            TCB tcb = new TCB(ipAndPort, random.nextInt(Short.MAX_VALUE + 1), tcpHeader.getSequenceNumber(), tcpHeader.getSequenceNumber() + 1,
                    tcpHeader.getAckNumber(), outputChannel, qPack, this.clientAddress, System.currentTimeMillis());
            

            try
            {
            	
                outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                if (outputChannel.finishConnect())
                {
                	tcb.status = TCBStatus.SYN_RECEIVED;
                    TCPHeader hdr = tcb.templatePack.getTCPHeader();
                    hdr.setAckNumber(tcb.mySequenceNum);
                    hdr.setSequenceNumber(tcb.myAcknowledgementNum);
                    
                    byte[] synAck = tpFactory.createSynAckPacketData(qPack.getIPHeader(), hdr).getBuffer();
                    responseBuffer.put(synAck);
                    //tcb.referenceHeader.setTCPheader(hdr);
                    //byte version = (byte) (synAck[0] >> 4);
            		//IPHeader ipHeader= IPPacketFactory.createIPv4Header(version, synAck,0);
            		//tcpHeader = tpFactory.createTCPHeader(synAck, ipHeader.getIPHeaderLength());
            		
            		
            		//tcb.templatePack.setIPHeader(ipHeader);
            		//tcb.templatePack.setTCPheader(tcpHeader);
            		
                    tcb.mySequenceNum++; // SYN counts as a byte
                    
                    
                    //responseBuffer.limit(HEADER_SIZE+currentPacket.tcpHeader.optionsAndPadding.length);
                    System.out.println(TAG+"Syn ACK sent "+responseBuffer.toString());
                    //tcb.status = TCBStatus.SYN_SENT;
                }
                else
                {
                	System.out.println(TAG+"Syn sent OP_CONNECT" );
                    tcb.status = TCBStatus.SYN_SENT;
                    selector.wakeup();
                    tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_CONNECT, tcb);
                    return;
                }
                
                
                TCB.putTCB(ipAndPort, tcb);
               
            }
            catch (IOException e)
            {
                System.out.println(TAG+ "Connection error: " + ipAndPort+ e.toString());
                //currentPacket.updateTCPBuffer(responseBuffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNum, 0, 0);
                
                
                //responseBuffer.limit(HEADER_SIZE);
                System.out.println(TAG+" RST sent "+responseBuffer.toString());
                TCB.closeTCB(tcb);
            }        
        }
        else
        {
        	    tcpHeader.setSequenceNumber(tcpHeader.getSequenceNumber() + 1);
            	byte[] rstData = tpFactory.createRstData(qPack.getIPHeader(), tcpHeader,0);
            	responseBuffer.put(rstData).limit(rstData.length);
            	System.out.println(TAG+"RST "+responseBuffer.toString());
            	
        }
        
        responseBuffer.rewind();
        outputQueue.offer(new QuipuRecord(this.clientAddress,responseBuffer));
    }

    private void processDuplicateSYN(TCB tcb, TCPHeader tcpHeader, ByteBuffer responseBuffer)
    {
        synchronized (tcb)
        {
            if (tcb.status == TCBStatus.SYN_SENT)
            {
                tcb.myAcknowledgementNum = tcpHeader.getSequenceNumber() + 1;
                return;
            }
        }
        sendRST(tcb, tcpHeader, 1, responseBuffer);
    }

    private void processFIN(TCB tcb, TCPHeader tcpHeader, ByteBuffer responseBuffer)
    {
        synchronized (tcb)
        {
            Packet qpuPack = tcb.templatePack;
            tcb.myAcknowledgementNum = tcpHeader.getSequenceNumber() + 1;
            tcb.theirAcknowledgementNum = tcpHeader.getAckNumber();
            
            
            // this assignment is fine. We can feed the packet directly to the API, it would update there.

            if (tcb.waitingForNetworkData)
            {
                tcb.status = TCBStatus.CLOSE_WAIT;
                tcpHeader.setSequenceNumber(tcb.mySequenceNum);
                tcpHeader.setAckNumber(tcb.myAcknowledgementNum);
                byte[] ackData = tpFactory.createResponseAckData(qpuPack.getIPHeader(), tcpHeader, tcb.myAcknowledgementNum);
                responseBuffer.put(ackData).limit(ackData.length);                
                System.out.println(TAG+"ACK sent "+responseBuffer.toString());
            }
            else
            {
                tcb.status = TCBStatus.LAST_ACK;
                byte[] finAckData = tpFactory.createFinAckData(qpuPack.getIPHeader(), tcpHeader, tcb.myAcknowledgementNum, tcb.mySequenceNum, true, true);
                responseBuffer.put(finAckData).limit(finAckData.length);
                
                tcb.mySequenceNum++; // FIN counts as a byte
                //responseBuffer.limit(HEADER_SIZE);
                System.out.println(TAG+"FIN "+responseBuffer.toString());
            }
            
            
        }
        responseBuffer.rewind();
        outputQueue.offer(new QuipuRecord(this.clientAddress,responseBuffer));
    }

    private void processACK(TCB tcb, TCPHeader tcpHeader, ByteBuffer payloadBuffer, ByteBuffer responseBuffer) throws IOException
    {
	
        int payloadSize = payloadBuffer.limit() - payloadBuffer.position();
        System.out.println(TAG+"Payload Size. "+payloadSize);
        synchronized (tcb)
        {
            SocketChannel outputChannel = tcb.channel;
            if (tcb.status == TCBStatus.SYN_RECEIVED)
            {
                tcb.status = TCBStatus.ESTABLISHED;

                selector.wakeup();
                tcb.selectionKey = outputChannel.register(selector, SelectionKey.OP_READ, tcb);
                tcb.waitingForNetworkData = true;
            }
            else if (tcb.status == TCBStatus.LAST_ACK)
            {
                closeCleanly(tcb, responseBuffer);
                return;
            }

            if (payloadSize == 0) return; // Empty ACK, ignore

            if (!tcb.waitingForNetworkData)
            {
                selector.wakeup();
                tcb.selectionKey.interestOps(SelectionKey.OP_READ);
                tcb.waitingForNetworkData = true;
            }

            // Forward to remote server
            try
            {
            	System.out.println(TAG+"Sending daa to the remote server. "+payloadSize);
                while (payloadBuffer.hasRemaining())
                    outputChannel.write(payloadBuffer);
            }
            catch (IOException e)
            {
                System.out.println(TAG+ "Network write error: " + tcb.ipAndPort+ e.toString());
                sendRST(tcb, tcpHeader, payloadSize, responseBuffer);
                return;
            }

            // TODO: We don't expect out-of-order packets, but verify
            tcb.myAcknowledgementNum = tcpHeader.getSequenceNumber() + payloadSize;
            tcb.theirAcknowledgementNum = tcpHeader.getAckNumber();
            Packet qpuPack = tcb.templatePack;

            byte[] ackrcvData = tpFactory.createResponseAckData(qpuPack.getIPHeader(), tcpHeader, tcb.myAcknowledgementNum);
            responseBuffer.put(ackrcvData).limit(ackrcvData.length);
            //referencePacket.updateTCPBuffer(responseBuffer, (byte) TCPHeader.ACK, tcb.mySequenceNum, tcb.myAcknowledgementNum, localTime, 0);
            //responseBuffer.limit(HEADER_SIZE);
            //responseBuffer.limit(HEADER_SIZE);
            System.out.println(TAG+" Data ACK sent "+responseBuffer.toString());

            
	 }
        //outputQueue.offer(responseBuffer);
        responseBuffer.rewind();
        outputQueue.offer(new QuipuRecord(this.clientAddress,responseBuffer));
    }

    private void sendRST(TCB tcb, TCPHeader tcpHeader, int prevPayloadSize, ByteBuffer buffer)
    {

        byte[] rstData = tpFactory.createRstData(tcb.templatePack.getIPHeader(), tcpHeader, prevPayloadSize);
        buffer.put(rstData).limit(rstData.length);

        //tcb.referencePacket.updateTCPBuffer(buffer, (byte) TCPHeader.RST, 0, tcb.myAcknowledgementNum + prevPayloadSize, 0, 0);
        System.out.println(TAG+" RST sent "+buffer.toString());
        //buffer.limit(HEADER_SIZE);
	  //outputQueue.offer(buffer);
        buffer.rewind();
        outputQueue.offer(new QuipuRecord(this.clientAddress,buffer));
        TCB.closeTCB(tcb);
    }
    
    

    private void closeCleanly(TCB tcb, ByteBuffer buffer)
    {
        ByteBufferPool.release(buffer);
        TCB.closeTCB(tcb);
    }
}
