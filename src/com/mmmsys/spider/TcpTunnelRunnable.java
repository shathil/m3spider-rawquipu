package com.mmmsys.spider;


import java.io.IOException;
import java.math.BigInteger;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Arrays;


public  class TcpTunnelRunnable implements Runnable
{
    private static final String TAG = QuipuTunnelRunnable.class.getSimpleName();

    private Map<String, InetSocketAddress> sockAadd = new HashMap<>();
    
    //private DatagramSocket tunchan;
    private SocketChannel tunchan;
    private ConcurrentLinkedQueue<PacketInfo> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<PacketInfo> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue;
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

    public TcpTunnelRunnable(SocketChannel proxysock, ConcurrentLinkedQueue<PacketInfo> deviceToNetworkUDPQueue2,
			ConcurrentLinkedQueue<PacketInfo> deviceToNetworkTCPQueue2,
			ConcurrentLinkedQueue<ByteBuffer> networkToDeviceQueue2) {
		// TODO Auto-generated constructor stub
        this.tunchan = proxysock;
        this.deviceToNetworkUDPQueue = deviceToNetworkUDPQueue2;
        this.deviceToNetworkTCPQueue = deviceToNetworkTCPQueue2;
        this.networkToDeviceQueue = networkToDeviceQueue2;

	}

	public static ByteBuffer leftShift(final ByteBuffer buffer, int shift) {
        final BigInteger bigInt = new BigInteger(buffer.array());
        final byte[] shiftedBytes = bigInt.shiftLeft(shift).and(allOnes((buffer.remaining()) * BYTE_LENGTH))
                .toByteArray();/*from  w  ww .j av  a 2 s  . co m*/
        final int resultLength = buffer.capacity();
        final int sourceOffset = resultLength >= shiftedBytes.length ? 0 : 1;
        final int destinationOffset = resultLength - shiftedBytes.length > 0 ? resultLength - shiftedBytes.length
                : 0;
        Arrays.fill(buffer.array(), (byte) 0);
        System.arraycopy(shiftedBytes, sourceOffset, buffer.array(), destinationOffset,
                shiftedBytes.length - sourceOffset);
        return buffer;
    }

    private static BigInteger allOnes(final int length) {
        return BigInteger.ZERO.setBit(length).subtract(BigInteger.ONE);
    }

    
    /*
    public void runUdp()
    {
    	System.out.println(TAG +" Started"); 

    	try{
            ByteBuffer bufferToNetwork = null;
            boolean dataSent = true;
            boolean dataReceived;
            int pos = 0;
            
            
            
            while (!Thread.interrupted())
            {
                // TODO: Block when not connected
                
            	byte[] rbuf = new byte[4096];
            	DatagramPacket dPacket = new DatagramPacket(rbuf, rbuf.length);

                this.tunchan.receive(dPacket);
                int readBytes = dPacket.getLength();
                //System.out.println("I am here for reading "+readBytes);
                if(readBytes > 0){
                	bufferToNetwork = ByteBuffer.wrap(dPacket.getData());
                	//bufferToNetwork.limit(readBytes);
                	M3Packet packet = new M3Packet(bufferToNetwork);
                
					System.out.println("stored packet "+ packet.toString());

					
					if(packet.isTCP()){ 
						deviceToNetworkTCPQueue.offer(packet);
						String keyPacket = packet.ip4Header.sourceAddress.getHostAddress()+":"+packet.tcpHeader.sourcePort;
						sockAadd.put(keyPacket, (InetSocketAddress)dPacket.getSocketAddress());
						dataSent = true;
					}
					else if (packet.isUDP()){
						deviceToNetworkUDPQueue.offer(packet);
						String keyPacket = packet.ip4Header.sourceAddress.getHostAddress()+":"+packet.udpHeader.sourcePort;
						sockAadd.put(keyPacket, (InetSocketAddress) dPacket.getSocketAddress());
						
						dataSent = true;
					}
					else {
						dataSent = false;
						System.out.println("Unknown Packet Header "+ packet.ip4Header.toString());
						
					}
					bufferToNetwork.clear();
					System.out.println("cclient IP "+ dPacket.getAddress().getHostAddress() +":"+dPacket.getPort());

                }
                else
                {
                    dataSent = false;
                }

                ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                
                if (bufferFromNetwork != null)
                	
                	
                	
                	
                {

                	bufferFromNetwork.flip();
                	byte[] sbuf = new byte[bufferFromNetwork.remaining()];
                    bufferFromNetwork.get(sbuf);
                	bufferFromNetwork.position(0);
                	bufferFromNetwork.limit(bufferFromNetwork.capacity());
                	M3Packet sep = new M3Packet(bufferFromNetwork);
                	int dstPort = 0;
                	String dstAddress = sep.ip4Header.destinationAddress.getHostAddress();
                	if(sep.isTCP()){ 
						dstPort = sep.tcpHeader.destinationPort;
					}
					else if (sep.isUDP()){
						dstPort = sep.udpHeader.destinationPort;
						dataSent = true;
					}
					String desSock = dstAddress+":"+ dstPort;
					System.out.println(desSock);
					InetSocketAddress desAddr = sockAadd.get(desSock);
                	InetAddress destinationAddress = desAddr.getAddress();
                	int destinationPort = desAddr.getPort();
                    DatagramPacket sPacket = new DatagramPacket(sbuf,sbuf.length,destinationAddress,destinationPort);


                    this.tunchan.send(sPacket);
                    dataReceived = true;
                    bufferFromNetwork.clear();
                }
                else
                {
                    dataReceived = false;
                }

                if (!dataSent && !dataReceived)
                    Thread.sleep(10);
           
	 
            } 

	
    	}
    	catch (InterruptedException e){	 
    		  System.out.println(TAG+" "+e.toString());
        }
        catch (IOException e)
        {
             System.out.println(TAG+" "+e.toString());
        }
        finally
        {
        	//closeAll();


        
        }
	    
    }*/
    
    public void run()
    {
    	System.out.println(TAG +" Started"); 

    	try{
            ByteBuffer bufferToNetwork = null;
            boolean dataSent = true;
            boolean dataReceived;
            int pos = 0;
            while (!Thread.interrupted())
            {
               if (dataSent)
                    bufferToNetwork = ByteBuffer.allocate(32450);
                //else
                //    bufferToNetwork.clear();
                // TODO: Block when not connected
                int readBytes = this.tunchan.read(bufferToNetwork);
                if (readBytes > 0)
                {
					bufferToNetwork.flip();
		            dataSent = true;
					int packetLength=0;
					int tracebackPos = 0;
	                while (bufferToNetwork.remaining()>=60)
	                {

                    	System.out.println("1 remaing buffer "+ bufferToNetwork.remaining()+" total packet length "+packetLength+" "+bufferToNetwork.toString());
                    	//readBytes = readBytes - packetLength;
                    	tracebackPos = bufferToNetwork.position();
                    	PacketInfo packet = new PacketInfo(bufferToNetwork);
                    	System.out.println("Packet Header "+ packet.ip4Header.toString());
                    	packetLength = packetLength+packet.ip4Header.totalLength;
                    	
						if (packetLength > readBytes){
							
							 bufferToNetwork.position(tracebackPos);
							 bufferToNetwork.limit(bufferToNetwork.capacity());
							 System.out.println("Fragmented paket");
							 break;
						}
						if(packet.isTCP()) 
							bufferToNetwork.position(packetLength);
						else if (packet.isUDP())
							bufferToNetwork.position(packetLength);
						else {
							System.out.println("This is a malpacket");
							bufferToNetwork.position(bufferToNetwork.limit()); 
						}
						//System.out.println("2 remaing buffer "+ packetLength+" "+bufferToNetwork.toString());
						//bufferToNetwork.position(packetLength);
                    	//System.out.println("2 remaing buffer "+ bufferToNetwork.remaining()+" "+bufferToNetwork.toString());
                    	

                    }
					 //System.out.println(readBytes+" bytes "+ bufferToNetwork.toString());	
					 //bufferToNetwork.clear();
					 //System.out.println(TAG+ bufferToNetwork.toString());


                }	
                
                else
                {
                    dataSent = false;
                }

                ByteBuffer bufferFromNetwork = networkToDeviceQueue.poll();
                if (bufferFromNetwork != null)
                {
                    bufferFromNetwork.flip();
                    while (bufferFromNetwork.hasRemaining())
                        this.tunchan.write(bufferFromNetwork);
                    dataReceived = true;

                    bufferFromNetwork.clear();
                }
                else
                {
                    dataReceived = false;
                }

                // TODO: Sleep-looping is not very battery-friendly, consider blocking instead
                // Confirm if throughput with ConcurrentQueue is really higher compared to BlockingQueue
                if (!dataSent && !dataReceived)
                    Thread.sleep(10);
           
	 
            } 

	
    	}
    	catch (InterruptedException e){	 
    		  System.out.println(TAG+" "+e.toString());
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
