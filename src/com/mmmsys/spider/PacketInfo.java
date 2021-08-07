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


import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Date;

import com.mmmsys.spider.PacketInfo.BitUtils;
import com.mmmsys.spider.PacketInfo.TCPHeader;
import com.mmmsys.spider.TCB.TCBStatus;

/**
 * Representation of an IP Packet
 */
// TODO: Reduce public mutability
public class PacketInfo
{
    public static final int IP4_HEADER_SIZE = 20;
    public static final int TCP_HEADER_SIZE = 20;
    public static final int UDP_HEADER_SIZE = 8;

    public IP4Header ip4Header;
    public TCPHeader tcpHeader;
    public UDPHeader udpHeader;
    public ByteBuffer backingBuffer;
    public QuipuRecord clientRecord;
    public SocketAddress clientAddress;

    private boolean isTCP;
    private boolean isUDP;

    public PacketInfo(ByteBuffer buffer) throws UnknownHostException {
        this.ip4Header = new IP4Header(buffer);
        if (this.ip4Header.protocol == IP4Header.TransportProtocol.TCP) {
            this.tcpHeader = new TCPHeader(buffer);
            this.isTCP = true;
        } else if (ip4Header.protocol == IP4Header.TransportProtocol.UDP) {
            this.udpHeader = new UDPHeader(buffer);
            this.isUDP = true;
        }
        this.backingBuffer = buffer;
        clientAddress = null;
        //System.out.println("BackingBuffer "+buffer.toString());
    }

    public void setClientAddress(SocketAddress clientAddress){
    	this.clientAddress = clientAddress;
    }
    
    public SocketAddress getClientAddress(){
    	return this.clientAddress;
    			
    }
    
    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder("Packet{");
        sb.append("ip4Header=").append(ip4Header);
        if (isTCP) sb.append(", tcpHeader=").append(tcpHeader);
        else if (isUDP) sb.append(", udpHeader=").append(udpHeader);
        sb.append(", payloadSize=").append(backingBuffer.limit() - backingBuffer.position());
        sb.append('}');
        return sb.toString();
    }

    public boolean isTCP()
    {
        return isTCP;
    }

    public boolean isUDP()
    {
        return isUDP;
    }

    public void swapSourceAndDestination()
    {
        InetAddress newSourceAddress = ip4Header.destinationAddress;
        ip4Header.destinationAddress = ip4Header.sourceAddress;
        ip4Header.sourceAddress = newSourceAddress;

        if (isUDP)
        {
            int newSourcePort = udpHeader.destinationPort;
            udpHeader.destinationPort = udpHeader.sourcePort;
            udpHeader.sourcePort = newSourcePort;
        }
        else if (isTCP)
        {
            int newSourcePort = tcpHeader.destinationPort;
            tcpHeader.destinationPort = tcpHeader.sourcePort;
            tcpHeader.sourcePort = newSourcePort;
        }
    }

    public void updateTCPBuffer(ByteBuffer buffer, byte flags, long sequenceNum, long ackNum,int initTime, int payloadSize)
    {
    	buffer.position(0);
        ip4Header.fillHeader(buffer);
	tcpHeader.fillHeader(buffer, initTime);

        backingBuffer = buffer;

        tcpHeader.flags = flags;
        backingBuffer.put(IP4_HEADER_SIZE + 13, flags);

        tcpHeader.sequenceNumber = sequenceNum;
        backingBuffer.putInt(IP4_HEADER_SIZE + 4, (int) sequenceNum);

        tcpHeader.acknowledgementNumber = ackNum;
        backingBuffer.putInt(IP4_HEADER_SIZE + 8, (int) ackNum);

        // Reset header size, since we don't need options
        if(tcpHeader.optionsLength==0){
	byte dataOffset = (byte) (TCP_HEADER_SIZE << 2);
        tcpHeader.dataOffsetAndReserved = dataOffset;
        backingBuffer.put(IP4_HEADER_SIZE + 12, dataOffset);
	}
        updateTCPChecksum(payloadSize);

        int ip4TotalLength = IP4_HEADER_SIZE + TCP_HEADER_SIZE + payloadSize+tcpHeader.optionsLength;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
    }
    
	public void extractOptionData(byte [] options) {

                //byte[] options = head.getOptions();
                byte kind;
                for (int i = 0; i < options.length; i++) {
                        kind = options[i];
                        if (kind == 2) {
                                i += 2;
                                int segSize = BitUtils.getNetworkInt(options, i, 2);
                                System.out.println("APcaket TCP segment size " + segSize);
                                i++;
                        } else if (kind == 3) {
                                i += 2;
                                int scale = BitUtils.getNetworkInt(options, i, 1);
                                //System.out.println("APcaket TCP segment size " + this.scale);
                        } else if (kind == 4) {
                                i++;
                                //head.setSelectiveAckPermitted(true);
                        } else if (kind == 5) {//SACK => selective acknowledgment
                                i++;
                                int sacklength = BitUtils.getNetworkInt(options, i, 1);
                                i = i + (sacklength - 2);
                                //case 10, 18, 26 and 34
                                //TODO: handle missing segments
                                //rare case => low priority

                        } else if (kind == 8) {//timestamp and echo of previous timestamp
                                i += 2;
                                int theirTime = BitUtils.getNetworkInt(options, i, 4);
                                System.out.println("APcaket sender " + theirTime);
                                i += 4;
                                int myTime = BitUtils.getNetworkInt(options, i, 4);
                                System.out.println("APcaket replyto " + myTime);
                                i += 3;
                        }
                }
        }


    
    private void updateOptions(int initTime){
    	
    	
    	//set response timestamps in options fields
		//tcp.setTimeStampReplyTo(tcp.getTimeStampSender());
		//Date currentdate = new Date();
		//long sendertimestamp = currentdate.getTime()/1000;
		int tsVal = initTime;
		//tcp.setTimeStampSender(sendertimestamp);

    		if(tcpHeader.optionsAndPadding.length>0){
    	    System.out.println("PacketInfo Editing Options" + backingBuffer.toString());
    		byte kind;
    		byte len;
    		for (int i = 0; i < tcpHeader.optionsAndPadding.length; i++) {
    			kind = tcpHeader.optionsAndPadding[i];
    			if (kind > 1) {
    				
    				
    				if (kind == 8) {//timestamp
    					i += 2;
    					if ((i + 7) < tcpHeader.optionsAndPadding.length) {
    						BitUtils.writeIntToBytes(tsVal, tcpHeader.optionsAndPadding, i);
    						i += 4;
    						BitUtils.writeIntToBytes(tcpHeader.theirTime, tcpHeader.optionsAndPadding, i);
    					}
    					break;
    					
    				} else if ((i + 1) < tcpHeader.optionsAndPadding.length) {
    					len = tcpHeader.optionsAndPadding[i + 1];
    					i = i + len - 1;
    				}
    			}
    		}
    		
    		
    	}

    	
    }
    
    public void updateUDPBuffer(ByteBuffer buffer, int payloadSize)
    {
        buffer.position(0);
        fillHeader(buffer);
        backingBuffer = buffer;

        int udpTotalLength = UDP_HEADER_SIZE + payloadSize;
        backingBuffer.putShort(IP4_HEADER_SIZE + 4, (short) udpTotalLength);
        udpHeader.length = udpTotalLength;

        // Disable UDP checksum validation
        backingBuffer.putShort(IP4_HEADER_SIZE + 6, (short) 0);
        udpHeader.checksum = 0;

        int ip4TotalLength = IP4_HEADER_SIZE + udpTotalLength;
        backingBuffer.putShort(2, (short) ip4TotalLength);
        ip4Header.totalLength = ip4TotalLength;

        updateIP4Checksum();
    }

    
    public long calculateChecksum(byte[] buf) {
        int length = buf.length;
        int i = 0;

        long sum = 0;
        long data;

        // Handle all pairs
        while (length > 1) {
          // Corrected to include @Andy's edits and various comments on Stack Overflow
          data = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
          sum += data;
          // 1's complement carry bit correction in 16-bits (detecting sign extension)
          if ((sum & 0xFFFF0000) > 0) {
            sum = sum & 0xFFFF;
            sum += 1;
          }

          i += 2;
          length -= 2;
        }

        // Handle remaining byte in odd length buffers
        if (length > 0) {
          // Corrected to include @Andy's edits and various comments on Stack Overflow
          sum += (buf[i] << 8 & 0xFF00);
          // 1's complement carry bit correction in 16-bits (detecting sign extension)
          if ((sum & 0xFFFF0000) > 0) {
            sum = sum & 0xFFFF;
            sum += 1;
          }
        }

        // Final 1's complement value correction to 16-bits
        sum = ~sum;
        sum = sum & 0xFFFF;
        return sum;

      }

    
    
    private void updateIP4Checksum()
    {
        ByteBuffer buffer = backingBuffer.duplicate();
        buffer.position(0);

        // Clear previous checksum
        buffer.putShort(10, (short) 0);

        int ipLength = ip4Header.headerLength;
        int sum = 0;
        while (ipLength > 0)
        {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            ipLength -= 2;
        }
        while (sum >> 16 > 0)
            sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        sum = sum & 0xFFFF;
        ip4Header.headerChecksum = sum;
        backingBuffer.putShort(10, (short) sum);
    }

    private void updateTCPChecksum(int payloadSize)
    {
        int sum = 0;
        int tcpLength = TCP_HEADER_SIZE + payloadSize;

        // Calculate pseudo-header checksum
        ByteBuffer buffer = ByteBuffer.wrap(ip4Header.sourceAddress.getAddress());
        sum = BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());

        buffer = ByteBuffer.wrap(ip4Header.destinationAddress.getAddress());
        sum += BitUtils.getUnsignedShort(buffer.getShort()) + BitUtils.getUnsignedShort(buffer.getShort());

        sum += IP4Header.TransportProtocol.TCP.getNumber() + tcpLength;

        buffer = backingBuffer.duplicate();
        // Clear previous checksum
        buffer.putShort(IP4_HEADER_SIZE + 16, (short) 0);

        // Calculate TCP segment checksum
        buffer.position(IP4_HEADER_SIZE);
        while (tcpLength > 1)
        {
            sum += BitUtils.getUnsignedShort(buffer.getShort());
            tcpLength -= 2;
        }
        if (tcpLength > 0)
            sum += BitUtils.getUnsignedByte(buffer.get()) << 8;

        while (sum >> 16 > 0)
            sum = (sum & 0xFFFF) + (sum >> 16);

        sum = ~sum;
        tcpHeader.checksum = sum;
        backingBuffer.putShort(IP4_HEADER_SIZE + 16, (short) sum);
    }

    private void fillHeader(ByteBuffer buffer)
    {
        ip4Header.fillHeader(buffer);
        if (isUDP)
            udpHeader.fillHeader(buffer);
       // else if (isTCP)
        //	tcpHeader.fillHeader(buffer);
    }

    public static class IP4Header
    {
        public byte version;
        public byte IHL;
        public int headerLength;
        public short typeOfService;
        public int totalLength;

        public int identificationAndFlagsAndFragmentOffset;

        public short TTL;
        private short protocolNum;
        public TransportProtocol protocol;
        public int headerChecksum;

        public InetAddress sourceAddress;
        public InetAddress destinationAddress;

        public int optionsAndPadding;

        private enum TransportProtocol
        {
            TCP(6),
            UDP(17),
            Other(0xFF);

            private int protocolNumber;

            TransportProtocol(int protocolNumber)
            {
                this.protocolNumber = protocolNumber;
            }

            private static TransportProtocol numberToEnum(int protocolNumber)
            {
                if (protocolNumber == 6)
                    return TCP;
                else if (protocolNumber == 17)
                    return UDP;
                else
                    return Other;
            }

            public int getNumber()
            {
                return this.protocolNumber;
            }
        }

        private IP4Header(ByteBuffer buffer) throws UnknownHostException
        {
            byte versionAndIHL = buffer.get();
            this.version = (byte) (versionAndIHL >> 4);
            this.IHL = (byte) (versionAndIHL & 0x0F);
            this.headerLength = this.IHL << 2;

            this.typeOfService = BitUtils.getUnsignedByte(buffer.get());
            this.totalLength = BitUtils.getUnsignedShort(buffer.getShort());

            this.identificationAndFlagsAndFragmentOffset = buffer.getInt();

            this.TTL = BitUtils.getUnsignedByte(buffer.get());
            this.protocolNum = BitUtils.getUnsignedByte(buffer.get());
            this.protocol = TransportProtocol.numberToEnum(protocolNum);
            this.headerChecksum = BitUtils.getUnsignedShort(buffer.getShort());

            byte[] addressBytes = new byte[4];
            buffer.get(addressBytes, 0, 4);
            this.sourceAddress = InetAddress.getByAddress(addressBytes);

            buffer.get(addressBytes, 0, 4);
            this.destinationAddress = InetAddress.getByAddress(addressBytes);

            //this.optionsAndPadding = buffer.getInt();
        }

        public void fillHeader(ByteBuffer buffer)
        {
            buffer.put((byte) (this.version << 4 | this.IHL));
            buffer.put((byte) this.typeOfService);
            buffer.putShort((short) this.totalLength);

            buffer.putInt(this.identificationAndFlagsAndFragmentOffset);

            buffer.put((byte) this.TTL);
            buffer.put((byte) this.protocol.getNumber());
            buffer.putShort((short) this.headerChecksum);

            buffer.put(this.sourceAddress.getAddress());
            buffer.put(this.destinationAddress.getAddress());
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder("IP4Header{");
            sb.append("version=").append(version);
            sb.append(", IHL=").append(IHL);
            sb.append(", typeOfService=").append(typeOfService);
            sb.append(", totalLength=").append(totalLength);
            sb.append(", identificationAndFlagsAndFragmentOffset=").append(identificationAndFlagsAndFragmentOffset);
            sb.append(", TTL=").append(TTL);
            sb.append(", protocol=").append(protocolNum).append(":").append(protocol);
            sb.append(", headerChecksum=").append(headerChecksum);
            sb.append(", sourceAddress=").append(sourceAddress.getHostAddress());
            sb.append(", destinationAddress=").append(destinationAddress.getHostAddress());
            sb.append('}');
            return sb.toString();
        }
    }

    public static class TCPHeader
    {
        public static final int FIN = 0x01;
        public static final int SYN = 0x02;
        public static final int RST = 0x04;
        public static final int PSH = 0x08;
        public static final int ACK = 0x10;
        public static final int URG = 0x20;

        public int sourcePort;
        public int destinationPort;

        public long sequenceNumber;
        public long acknowledgementNumber;

        public byte dataOffsetAndReserved;
        public int headerLength;
        public byte flags;
        public int window;

        public int checksum;
        public int urgentPointer;

        
        public byte[] optionsAndPadding;
        
        public int segSize;
        public int theirTime;
        public int myTime;
        public int scale;
        public int optionsLength;

        private TCPHeader(ByteBuffer buffer)
        {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
            this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());

            this.sequenceNumber = BitUtils.getUnsignedInt(buffer.getInt());
            this.acknowledgementNumber = BitUtils.getUnsignedInt(buffer.getInt());

            this.dataOffsetAndReserved = buffer.get();
            this.headerLength = (this.dataOffsetAndReserved & 0xF0) >> 2;
            this.flags = buffer.get();
            this.window = BitUtils.getUnsignedShort(buffer.getShort());

            this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
            this.urgentPointer = BitUtils.getUnsignedShort(buffer.getShort());

            this.optionsLength = this.headerLength - TCP_HEADER_SIZE;
            if (this.optionsLength > 0)
            {
                optionsAndPadding = new byte[this.optionsLength];
                buffer.get(optionsAndPadding, 0, this.optionsLength);
                this.extractOptionData(optionsAndPadding);
            }
        }
        
        
        
    	public void extractOptionData(byte [] options) {

    		//byte[] options = head.getOptions();
    		byte kind;
    		for (int i = 0; i < options.length; i++) {
    			kind = options[i];
    			if (kind == 2) {
    				i += 2;
    				this.segSize = BitUtils.getNetworkInt(options, i, 2);
    				//System.out.println("APcaket TCP segment size " + segSize);
    				i++;
    			} else if (kind == 3) {
    				i += 2;
    				this.scale = BitUtils.getNetworkInt(options, i, 1);
    				//System.out.println("APcaket TCP segment size " + this.scale);
    			} else if (kind == 4) {
    				i++;
    				//head.setSelectiveAckPermitted(true);
    			} else if (kind == 5) {//SACK => selective acknowledgment
    				i++;
    				int sacklength = BitUtils.getNetworkInt(options, i, 1);
    				i = i + (sacklength - 2);
    				//case 10, 18, 26 and 34
    				//TODO: handle missing segments
    				//rare case => low priority
    			} else if (kind == 8) {//timestamp and echo of previous timestamp
    				i += 2;
    				this.theirTime = BitUtils.getNetworkInt(options, i, 4);
				//System.out.println("APcaket their time " + theirTime); 
   				i += 4;
    				this.myTime = BitUtils.getNetworkInt(options, i, 4);
				//System.out.println("APcaket myTime " + myTime);
    				i += 3;
    			}
    		}
    	}
    	
    	


        public boolean isFIN()
        {
            return (flags & FIN) == FIN;
        }

        public boolean isSYN()
        {
            return (flags & SYN) == SYN;
        }

        public boolean isRST()
        {
            return (flags & RST) == RST;
        }

        public boolean isPSH()
        {
            return (flags & PSH) == PSH;
        }

        public boolean isACK()
        {
            return (flags & ACK) == ACK;
        }

        public boolean isURG()
        {
            return (flags & URG) == URG;
        }

        private void fillHeader(ByteBuffer buffer, int initTime)
        {
            buffer.putShort((short) sourcePort);
            buffer.putShort((short) destinationPort);

            buffer.putInt((int) sequenceNumber);
            buffer.putInt((int) acknowledgementNumber);

            buffer.put(dataOffsetAndReserved);
            buffer.put(flags);
            buffer.putShort((short) window);

            buffer.putShort((short) checksum);
            buffer.putShort((short) urgentPointer);
	
		System.out.println("Packet extention "+ buffer.toString());            
	            // this is the spot to fill the additional header
            if(this.segSize>0){
		updateOptions(initTime);
		buffer.put(optionsAndPadding);
	    }    
        }


    private void updateOptions(int initTime){


                int tsVal = initTime;

                if(optionsAndPadding.length>0){
                byte kind;
                byte len;
                for (int i = 0; i < optionsAndPadding.length; i++) {
                        kind = optionsAndPadding[i];
                        if (kind > 1) {


                                if (kind == 8) {//timestamp
                                        i += 2;
                                        if ((i + 7) < optionsAndPadding.length) {
                                                BitUtils.writeIntToBytes(tsVal, optionsAndPadding, i);
                                                i += 4;
                                                BitUtils.writeIntToBytes(this.theirTime, optionsAndPadding, i);
                                        }
                                        break;

                                } else if ((i + 1) < optionsAndPadding.length) {
                                        len = optionsAndPadding[i + 1];
                                        i = i + len - 1;
                                }
                        }
                }


        }


    }


        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder("TCPHeader{");
            sb.append("sourcePort=").append(sourcePort);
            sb.append(", destinationPort=").append(destinationPort);
            sb.append(", sequenceNumber=").append(sequenceNumber);
            sb.append(", acknowledgementNumber=").append(acknowledgementNumber);
            sb.append(", headerLength=").append(headerLength);
            sb.append(", window=").append(window);
            sb.append(", checksum=").append(checksum);
            sb.append(", flags=");
            if (isFIN()) sb.append(" FIN");
            if (isSYN()) sb.append(" SYN");
            if (isRST()) sb.append(" RST");
            if (isPSH()) sb.append(" PSH");
            if (isACK()) sb.append(" ACK");
            if (isURG()) sb.append(" URG");
            sb.append('}');
            return sb.toString();
        }
    }

    public static class UDPHeader
    {
        public int sourcePort;
        public int destinationPort;

        public int length;
        public int checksum;

        private UDPHeader(ByteBuffer buffer)
        {
            this.sourcePort = BitUtils.getUnsignedShort(buffer.getShort());
            this.destinationPort = BitUtils.getUnsignedShort(buffer.getShort());

            this.length = BitUtils.getUnsignedShort(buffer.getShort());
            this.checksum = BitUtils.getUnsignedShort(buffer.getShort());
        }

        private void fillHeader(ByteBuffer buffer)
        {
            buffer.putShort((short) this.sourcePort);
            buffer.putShort((short) this.destinationPort);

            buffer.putShort((short) this.length);
            buffer.putShort((short) this.checksum);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder("UDPHeader{");
            sb.append("sourcePort=").append(sourcePort);
            sb.append(", destinationPort=").append(destinationPort);
            sb.append(", length=").append(length);
            sb.append(", checksum=").append(checksum);
            sb.append('}');
            return sb.toString();
        }
    }

    public static class BitUtils
    {
        private static short getUnsignedByte(byte value)
        {
            return (short)(value & 0xFF);
        }

        private static int getUnsignedShort(short value)
        {
            return value & 0xFFFF;
        }

        private static long getUnsignedInt(int value)
        {
            return value & 0xFFFFFFFFL;
        }
        
    	public static short getNetworkShort(byte[] buffer, int start) {
    		short value = 0x0000;
    		value |= buffer[start] & 0xFF;
    		value <<= 8;
    		value |= buffer[start + 1] & 0xFF;
    		return value;
    	}

    	/**
    	 * convert array of byte to int
    	 *
    	 * @param buffer
    	 * @param start
    	 * @param length
    	 * @return value of int
    	 */
    	public static int getNetworkInt(byte[] buffer, int start, int length) {
    		int value = 0x00000000;
    		int end = start + (length > 4 ? 4 : length);
    		if (end > buffer.length) {
    			end = buffer.length;
    		}
    		for (int i = start; i < end; i++) {
    			value |= buffer[i] & 0xFF;
    			if (i < (end - 1)) {
    				value <<= 8;
    			}
    		}
    		return value;
    	}
    	
    	public static void writeIntToBytes(int value, byte[] buffer, int offset) {
    		if (buffer.length - offset < 4) {
    			return;
    		}
    		buffer[offset] = (byte) ((value >> 24) & 0x000000FF);
    		buffer[offset + 1] = (byte) ((value >> 16) & 0x000000FF);
    		buffer[offset + 2] = (byte) ((value >> 8) & 0x000000FF);
    		buffer[offset + 3] = (byte) (value & 0x000000FF);
    	}

    	/**
    	 * convert array of byte to unsignedint
    	 * Java doesnt support unsigned int's and we have to handle them as longs
    	 * http://www.javamex.com/java_equivalents/unsigned_arithmetic.shtml
    	 * http://packetlife.net/blog/2010/jun/7/understanding-tcp-sequence-acknowledgment-numbers/
    	 * @param buffer
    	 * @param start
    	 * @param length
    	 * @return value of long
    	 */
    	public static long getNetworkUnsignedInt(byte[] buffer, int start, int length) {
    		long value = 0x00000000L;
    		int end = start + (length > 4 ? 4 : length);
    		if (end > buffer.length) {
    			end = buffer.length;
    		}
    		for (int i = start; i < end; i++) {
    			value |= buffer[i] & 0xFF;
    			if (i < (end - 1)) {
    				value <<= 8;
    			}
    		}
    		return value;
    	}
    }
}
