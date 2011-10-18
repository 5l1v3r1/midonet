package com.midokura.midolman.packets;

import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

public class TestIPv4 {

    @Test
    public void testCksumZeroArrays() {
        byte[] bytes = new byte[100];
        short expCksum = (short) 0xffff;
        short cksum = IPv4.computeChecksum(bytes, 0, 0, 0);
        Assert.assertEquals(expCksum, cksum);
        cksum = IPv4.computeChecksum(bytes, 0, 100, 100);
        Assert.assertEquals(expCksum, cksum);
        cksum = IPv4.computeChecksum(bytes, 1, 99, 100);
        Assert.assertEquals(expCksum, cksum);
    }

    @Test
    public void testCksumRealData() {
        // An ICMP echo reply from tcpdump with checksum zeroed (bytes 2, 3).
        byte[] data = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0xb2, (byte)0x78, (byte)0x00, (byte)0x01,
                (byte)0xf8, (byte)0x59, (byte)0x98, (byte)0x4e,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0xc6, (byte)0xec, (byte)0x0b, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x10, (byte)0x11, (byte)0x12, (byte)0x13,
                (byte)0x14, (byte)0x15, (byte)0x16, (byte)0x17,
                (byte)0x18, (byte)0x19, (byte)0x1a, (byte)0x1b,
                (byte)0x1c, (byte)0x1d, (byte)0x1e, (byte)0x1f,
                (byte)0x20, (byte)0x21, (byte)0x22, (byte)0x23,
                (byte)0x24, (byte)0x25, (byte)0x26, (byte)0x27,
                (byte)0x28, (byte)0x29, (byte)0x2a, (byte)0x2b,
                (byte)0x2c, (byte)0x2d, (byte)0x2e, (byte)0x2f,
                (byte)0x30, (byte)0x31, (byte)0x32, (byte)0x33,
                (byte)0x34, (byte)0x35, (byte)0x36, (byte)0x37
        };
        short cksum = IPv4.computeChecksum(data, 0, data.length, 0);
        data[0] = (byte) (cksum >> 8);
        data[1] = (byte) cksum;
        // Verify that the checksum field is ignored by getting the same cksum.
        Assert.assertEquals(cksum,
                IPv4.computeChecksum(data, 0, data.length, 0));
        // Now verify that when we don't ignore the cksum, we get zero.
        Assert.assertEquals(0, IPv4.computeChecksum(data, 0, data.length, -2));

        // Repeat with a different subset of the array (and odd length).
        cksum = IPv4.computeChecksum(data, 0, 45, 0);
        // Set the checksum field.
        data[0] = (byte) (cksum >> 8);
        data[1] = (byte) cksum;
        // Now verify that when we don't ignore the cksum, we get zero.
        Assert.assertEquals(0, IPv4.computeChecksum(data, 0, 45, -2));
    }

    @Test
    public void testCksumRandomArrays() {
        Random rand = new Random(12345);
        for (int i = 0; i < 10; i++) {
            // Generate a random length between 100 and 1000
            int length = rand.nextInt(900) + 100;
            byte[] data = new byte[length];
            rand.nextBytes(data);
            short cksum = IPv4.computeChecksum(data, 0, data.length, 0);
            data[0] = (byte) (cksum >> 8);
            data[1] = (byte) cksum;
            // Verify that if we don't ignore the cksum, we get zero.
            Assert.assertEquals(0,
                    IPv4.computeChecksum(data, 0, data.length, -2));
        }
    }

    @Test
    public void testSerializationICMP() {
        // An IP packet containing an ICMP echo reply (checksums zeroed). 
        byte[] ipBytes = new byte[] {
                (byte)0x45, (byte)0x00, (byte)0x00, (byte)0x54,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00,
                (byte)0xc0, (byte)0xa8, (byte)0x14, (byte)0x01,
                (byte)0xc0, (byte)0xa8, (byte)0x14, (byte)0x03,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0xb2, (byte)0x78, (byte)0x00, (byte)0x01,
                (byte)0xf8, (byte)0x59, (byte)0x98, (byte)0x4e,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0xc6, (byte)0xec, (byte)0x0b, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x10, (byte)0x11, (byte)0x12, (byte)0x13,
                (byte)0x14, (byte)0x15, (byte)0x16, (byte)0x17,
                (byte)0x18, (byte)0x19, (byte)0x1a, (byte)0x1b,
                (byte)0x1c, (byte)0x1d, (byte)0x1e, (byte)0x1f,
                (byte)0x20, (byte)0x21, (byte)0x22, (byte)0x23,
                (byte)0x24, (byte)0x25, (byte)0x26, (byte)0x27,
                (byte)0x28, (byte)0x29, (byte)0x2a, (byte)0x2b,
                (byte)0x2c, (byte)0x2d, (byte)0x2e, (byte)0x2f,
                (byte)0x30, (byte)0x31, (byte)0x32, (byte)0x33,
                (byte)0x34, (byte)0x35, (byte)0x36, (byte)0x37 };
        IPv4 ipPkt = new IPv4();
        // First deserialize/serialize the whole packet.
        ipPkt.deserialize(ipBytes, 0, ipBytes.length);
        byte[] bytes = ipPkt.serialize();
        // Verify the checksums.
        Assert.assertEquals(0x11, bytes[10]);
        Assert.assertEquals(0x55, bytes[11]);
        Assert.assertEquals(0x2c, bytes[22]);
        Assert.assertEquals(0x1e, bytes[23]);
        ipBytes[10] = 0x11;
        ipBytes[11] = 0x55;
        ipBytes[22] = 0x2c;
        ipBytes[23] = 0x1e;
        // Once checksums have been filled, the arrays should be equal.
        Assert.assertArrayEquals(ipBytes, bytes);

        // Now deserializae/serialize the same packet from a larger-than-needed
        // buffer.
        byte[] longBuffer = Arrays.copyOf(ipBytes, ipBytes.length+100);
        ipPkt = new IPv4();
        ipPkt.deserialize(longBuffer, 0, longBuffer.length);
        Assert.assertArrayEquals(ipBytes, ipPkt.serialize());

        // Now deserialize/serialize an incomplete packet. Note that the ICMP
        // has 56 bytes of data - we'll only deserialize some of it, but the
        // expected array is the same length as the original because it's
        // determined by the IPv4 totalLength field.
        Arrays.fill(ipBytes, ipBytes.length-20, ipBytes.length, (byte)0);
        ipPkt = new IPv4();
        ipPkt.deserialize(ipBytes, 0, ipBytes.length-20);
        Assert.assertArrayEquals(ipBytes, ipPkt.serialize());
    }

    @Test
    public void testSerializationDHCP() {
        // A DHCP Request packet from tcpdump, starting with IP headers.
        byte[] data = new byte[] {
                (byte)0x45, (byte)0x10, (byte)0x01, (byte)0x48,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x10, (byte)0x11, (byte)0xa9, (byte)0x96,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
                (byte)0x00, (byte)0x44, (byte)0x00, (byte)0x43,
                (byte)0x01, (byte)0x34, (byte)0x70, (byte)0x16,
                (byte)0x01, (byte)0x01, (byte)0x06, (byte)0x00,
                (byte)0x2e, (byte)0x86, (byte)0xe1, (byte)0x21,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x02, (byte)0x16, (byte)0x3e, (byte)0x26,
                (byte)0x14, (byte)0x99, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x63, (byte)0x82, (byte)0x53, (byte)0x63,
                (byte)0x35, (byte)0x01, (byte)0x03, (byte)0x32,
                (byte)0x04, (byte)0xc0, (byte)0xa8, (byte)0x14,
                (byte)0x03, (byte)0x37, (byte)0x0a, (byte)0x01,
                (byte)0x1c, (byte)0x02, (byte)0x03, (byte)0x0f,
                (byte)0x06, (byte)0x0c, (byte)0x28, (byte)0x29,
                (byte)0x2a, (byte)0xff, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00 };
        IPv4 ipPkt = new IPv4();
        // Deserialize/serialize the whole packet.
        ipPkt.deserialize(data, 0, data.length);
        // Basic sanity check: IPv4 contains a UDP packet from port 68 to 67.
        Assert.assertEquals(UDP.PROTOCOL_NUMBER, ipPkt.getProtocol());
        UDP udpPkt = (UDP)ipPkt.getPayload();
        Assert.assertEquals(68, udpPkt.getSourcePort());
        Assert.assertEquals(67, udpPkt.getDestinationPort());
        // Now re-serialize and verify we get the same bytes back.
        Assert.assertArrayEquals(data, ipPkt.serialize());

        // Now deserializae/serialize the same packet from a larger-than-needed
        // buffer.
        byte[] longBuffer = Arrays.copyOf(data, data.length+100);
        ipPkt = new IPv4();
        ipPkt.deserialize(longBuffer, 0, longBuffer.length);
        Assert.assertArrayEquals(data, ipPkt.serialize());

        // Now try a partial packet... lose 20 bytes from the IPv4.
        Arrays.fill(data, data.length-20, data.length, (byte)0);
        ipPkt = new IPv4();
        ipPkt.deserialize(data, 0, data.length-20);
        Assert.assertArrayEquals(data, ipPkt.serialize());
    }

    @Test
    public void testSerializationMDNS() {
        // IPv4 packet from tcpdump with UDP/MDNS payload. Has been truncated
        // to 114 bytes to simulate OpenFlow's 128 bytes which include Ethernet.
        byte[] data = new byte[] {
                (byte)0x45, (byte)0x00, (byte)0x00, (byte)0xf2,
                (byte)0x00, (byte)0x00, (byte)0x40, (byte)0x00,
                (byte)0xff, (byte)0x11, (byte)0xc5, (byte)0x53,
                (byte)0xc0, (byte)0xa8, (byte)0x14, (byte)0x03,
                (byte)0xe0, (byte)0x00, (byte)0x00, (byte)0xfb,
                (byte)0x14, (byte)0xe9, (byte)0x14, (byte)0xe9,
                (byte)0x00, (byte)0xde, (byte)0x4a, (byte)0x33,
                (byte)0x00, (byte)0x00, (byte)0x84, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x05,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x09, (byte)0x5f, (byte)0x73, (byte)0x65,
                (byte)0x72, (byte)0x76, (byte)0x69, (byte)0x63,
                (byte)0x65, (byte)0x73, (byte)0x07, (byte)0x5f,
                (byte)0x64, (byte)0x6e, (byte)0x73, (byte)0x2d,
                (byte)0x73, (byte)0x64, (byte)0x04, (byte)0x5f,
                (byte)0x75, (byte)0x64, (byte)0x70, (byte)0x05,
                (byte)0x6c, (byte)0x6f, (byte)0x63, (byte)0x61,
                (byte)0x6c, (byte)0x00, (byte)0x00, (byte)0x0c,
                (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00,
                (byte)0x11, (byte)0x94, (byte)0x00, (byte)0x14,
                (byte)0x0c, (byte)0x5f, (byte)0x77, (byte)0x6f,
                (byte)0x72, (byte)0x6b, (byte)0x73, (byte)0x74,
                (byte)0x61, (byte)0x74, (byte)0x69, (byte)0x6f,
                (byte)0x6e, (byte)0x04, (byte)0x5f, (byte)0x74,
                (byte)0x63, (byte)0x70, (byte)0xc0, (byte)0x23,
                (byte)0xc0, (byte)0x34, (byte)0x00, (byte)0x0c,
                (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00,
                (byte)0x11, (byte)0x94, (byte)0x00, (byte)0x20,
                (byte)0x1d, (byte)0x6c };
        IPv4 ipPkt = new IPv4();
        // Deserialize the whole packet.
        ipPkt.deserialize(data, 0, data.length);
        // Basic sanity check: IPv4 contains a UDP packet from/to port 5353.
        Assert.assertEquals(UDP.PROTOCOL_NUMBER, ipPkt.getProtocol());
        UDP udpPkt = (UDP)ipPkt.getPayload();
        Assert.assertEquals(5353, udpPkt.getSourcePort());
        Assert.assertEquals(5353, udpPkt.getDestinationPort());
        // Now re-serialize and verify we get the same bytes back. The
        // serialized array is longer than the original because the original
        // is truncated.
        byte[] expected = ipPkt.serialize();
        expected = Arrays.copyOf(expected, data.length);
        Assert.assertArrayEquals(data, expected);

        // NOTE: in this case we can't test for a buffer/byte-array that's
        // longer than the actual packet, because the packet has been truncated.
        // We would not be able to distinguish excess bytes from real payload.
    }

    @Test
    public void testSerializationSSH() {
        // SSH packet (starting with IPv4 headers) captured with tcpdump.
        byte[] data = new byte[] {
                (byte)0x45, (byte)0x10, (byte)0x00, (byte)0x64,
                (byte)0xec, (byte)0xbc, (byte)0x40, (byte)0x00,
                (byte)0x40, (byte)0x06, (byte)0x60, (byte)0xcf,
                (byte)0xc0, (byte)0xa8, (byte)0x01, (byte)0x85,
                (byte)0x0e, (byte)0x80, (byte)0x1c, (byte)0x4b,
                (byte)0xe5, (byte)0x0e, (byte)0x00, (byte)0x16,
                (byte)0x8d, (byte)0x3a, (byte)0x5d, (byte)0x09,
                (byte)0x0f, (byte)0x95, (byte)0xc4, (byte)0xe3,
                (byte)0x80, (byte)0x18, (byte)0xff, (byte)0xff,
                (byte)0x0a, (byte)0x46, (byte)0x00, (byte)0x00,
                (byte)0x01, (byte)0x01, (byte)0x08, (byte)0x0a,
                (byte)0x2f, (byte)0x1c, (byte)0x20, (byte)0x06,
                (byte)0x0e, (byte)0x20, (byte)0x8d, (byte)0x38,
                (byte)0xbd, (byte)0xfc, (byte)0xd7, (byte)0xa6,
                (byte)0x8d, (byte)0xc3, (byte)0x06, (byte)0x93,
                (byte)0x5f, (byte)0xdf, (byte)0x0e, (byte)0x11,
                (byte)0x49, (byte)0x4a, (byte)0x68, (byte)0xdc,
                (byte)0x30, (byte)0x8a, (byte)0x2b, (byte)0xdc,
                (byte)0xb2, (byte)0xb2, (byte)0xd4, (byte)0x0e,
                (byte)0xea, (byte)0xb5, (byte)0x1e, (byte)0xf9,
                (byte)0xd0, (byte)0xdf, (byte)0x26, (byte)0xbf,
                (byte)0x56, (byte)0xa6, (byte)0x65, (byte)0x36,
                (byte)0x07, (byte)0x9c, (byte)0x95, (byte)0x23,
                (byte)0x9d, (byte)0xd3, (byte)0xeb, (byte)0xa7,
                (byte)0x3c, (byte)0x68, (byte)0xa3, (byte)0xe3 };
        IPv4 ipPkt = new IPv4();
        // Deserialize the whole packet.
        ipPkt.deserialize(data, 0, data.length);
        // Basic sanity check: IPv4 contains a TCP packet to port 22 (ssh).
        Assert.assertEquals(TCP.PROTOCOL_NUMBER, ipPkt.getProtocol());
        TCP udpPkt = (TCP)ipPkt.getPayload();
        Assert.assertEquals(22, udpPkt.getDestinationPort());
        // Now re-serialize and verify we get the same bytes back. The
        // serialized array is longer than the original because the original
        // is truncated.
        byte[] expected = ipPkt.serialize();
        expected = Arrays.copyOf(expected, data.length);
        Assert.assertArrayEquals(data, expected);

        // Now deserializae/serialize the same packet from a larger-than-needed
        // buffer.
        byte[] longBuffer = Arrays.copyOf(data, data.length+100);
        ipPkt = new IPv4();
        ipPkt.deserialize(longBuffer, 0, longBuffer.length);
        Assert.assertArrayEquals(data, ipPkt.serialize());

        // Now try deserializaing/serializing a truncated packet.
        Arrays.fill(data, data.length-30, data.length, (byte)0);
        ipPkt = new IPv4();
        ipPkt.deserialize(data, 0, data.length-30);
        Assert.assertArrayEquals(data, ipPkt.serialize());
    }
}
