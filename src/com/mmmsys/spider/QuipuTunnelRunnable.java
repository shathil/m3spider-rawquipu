package com.mmmsys.spider;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.attn.protocol.Packet;
import com.mmmsys.spider.PacketInfo.TCPHeader;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;	

public  class QuipuTunnelRunnable implements Runnable
{
    private static final String TAG = QuipuTunnelRunnable.class.getSimpleName();

    private Map<String, InetSocketAddress> sockAadd = new HashMap<>();
    
    private DatagramSocket tunchan;
    //private SocketChannel tunchan;
    private ConcurrentLinkedQueue<PacketInfo> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<QuipuRecord> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<QuipuRecord> networkToDeviceQueue;
    private static final byte BYTE_LENGTH = 8;
   
    /*
    public M3TunnelRunnable(DatagramChannel sock,
                       ConcurrentLinkedQueue<M3Packet> deviceToNetworkUDPQueue,
                       ConcurrentLinkedQueue<M3Packet> deviceToNetworkTCPQueue,
                       ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue)
    {
        this.tunchan = sock;
        this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue;
        this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue;
        this.networkToDeviceQueue = networkToDeviceQueue;
    }*/

    public QuipuTunnelRunnable(DatagramSocket proxysock, ConcurrentLinkedQueue<PacketInfo> deviceToNetworkUDPQueue2,
			ConcurrentLinkedQueue<QuipuRecord> deviceToNetworkTCPQueue2,
			ConcurrentLinkedQueue<QuipuRecord> networkToDeviceQueue2) {
		// TODO Auto-generated constructor stub
        this.tunchan = proxysock;
        this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue2;
        this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue2;
        this.networkToDeviceQueue = networkToDeviceQueue2;

	}


    
    
    public void run()
    {
    	System.out.println(TAG +" Started"); 

        ByteBuffer bufferToNetwork = null;
        boolean dataSent = true;
        boolean dataReceived;
        int pos = 0;
  
    	while(true){
            
    		byte[] rbuf = new byte[4096];
        	DatagramPacket dPacket = new DatagramPacket(rbuf, rbuf.length);
        	
              
        	try{
        		//this.tunchan.setSoTimeout(10);
            	this.tunchan.receive(dPacket);
                //this.tunchan.setSoTimeout(0);
                int readBytes = dPacket.getLength();
                //System.out.println("I am here for reading "+readBytes);
                if(readBytes > 0){
                	bufferToNetwork = ByteBuffer.wrap(dPacket.getData());
                	PacketInfo packet = new PacketInfo(bufferToNetwork);
                	
                	bufferToNetwork.rewind();
                	bufferToNetwork.limit(readBytes);
                	
                	QuipuRecord qtcpRecord = new QuipuRecord(dPacket.getSocketAddress(), bufferToNetwork);
                	packet.setClientAddress(dPacket.getSocketAddress());
                	
                	
					
					if(packet.isTCP()){ 
						
						deviceToNetworkTCPQueue.offer(qtcpRecord);
						//String keyPacket = packet.ip4Header.sourceAddress.getHostAddress()+":"+packet.tcpHeader.sourcePort;
						//sockAadd.put(keyPacket, (InetSocketAddress)dPacket.getSocketAddress());
						if(packet.tcpHeader.isSYN())
							System.out.println(dPacket.getAddress().getHostAddress()+" TCP packet "+ packet.toString());
						dataSent = true;
					}
					else if (packet.isUDP()){
						deviceToNetworkUDPQueue.offer(packet);
						
						//String keyPacket = packet.ip4Header.sourceAddress.getHostAddress()+":"+packet.udpHeader.sourcePort;
						//sockAadd.put(keyPacket, (InetSocketAddress) dPacket.getSocketAddress());
						//System.out.println(dPacket.getAddress().getHostAddress()+" UDP packet "+ packet.toString());
						//System.out.println("cclient IP "+ dPacket.getAddress().getHostAddress() +":"+dPacket.getPort());
						dataSent = true;
					}
					else {
						dataSent = false;
						System.out.println("Unknown Packet Header "+ packet.ip4Header.toString());
						
					}
					bufferToNetwork.clear();
					

                }
                else
                {
                    dataSent = false;
                }
        	}catch(SocketTimeoutException se){
                QuipuRecord clientRecord = networkToDeviceQueue.poll();
                //System.out.println("Trying to Write...................");
                if (clientRecord != null)
                {

                	//clientRecord.buffer.flip();
                	//System.out.println("Initial Buffer "+ clientRecord.buffer.toString());
                	byte[] sbuf = new byte[clientRecord.buffer.remaining()];
                	clientRecord.buffer.get(sbuf);

                	InetSocketAddress desAddr = (InetSocketAddress) clientRecord.clientAddress;
                	InetAddress destinationAddress = desAddr.getAddress();
                	int destinationPort = desAddr.getPort();
                    DatagramPacket sPacket = new DatagramPacket(sbuf,sbuf.length,destinationAddress,destinationPort);
                         
	              		try {
	              			this.tunchan.send(sPacket);
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
						dataReceived = false;
					}
	                	//this.tunchan.send(sPacket);
                    clientRecord.buffer.rewind();
                    PacketInfo npack;
					try {
						npack = new PacketInfo(clientRecord.buffer);
						if(npack.isTCP())
	                    System.out.println("Sending data for "+ sPacket.getLength()+" "+npack.tcpHeader.segSize+" "+npack.tcpHeader.theirTime+" "+npack.tcpHeader.myTime); 

					} catch (UnknownHostException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

                   
                    dataReceived = true;
                    //clientRecord.buffer.clear();
                }
                else
                {
                    dataReceived = false;
                }
		}

	 
             

	
    	
    	catch (IOException e)
        {
             System.out.println(TAG+" "+e.toString());
        }
        finally
        {
        	//closeAll();


        
        }
	}
	    
    }
        
    
}
