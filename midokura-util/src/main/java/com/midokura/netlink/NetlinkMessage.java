/*
* Copyright 2012 Midokura KK
*/
package com.midokura.netlink;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import com.midokura.netlink.clib.cLibrary;
import com.midokura.netlink.messages.Builder;
import com.midokura.netlink.messages.BuilderAware;

/**
 * Abstraction for a netlink message. It provides a builder to allow easy serialization
 * of netlink messages (using the same target buffer) and easy way to deserialize
 * a message.
 */
public class NetlinkMessage {

    public static class AttrKey<Type> {

        short id;

        public AttrKey(int id) {
            this(id, false);
        }

        public AttrKey(int id, boolean nested) {
            this.id = nested
                        ? (short) (id | NetlinkMessage.NLA_F_NESTED)
                        : (short) id;
        }

        public short getId() {
            return id;
        }
    }

    public interface Attr<T> {

        public AttrKey<T> getKey();

        public T getValue();
    }

    static public final short NLA_F_NESTED = (short) (1 << 15);
    static public final short NLA_F_NET_BYTEORDER = (1 << 14);
    static public final short NLA_TYPE_MASK = ~NLA_F_NESTED | NLA_F_NET_BYTEORDER;

    private ByteBuffer buf;
    private ByteOrder byteOrder;

    public NetlinkMessage(int size) {
        buf = ByteBuffer.allocateDirect(size);
        buf.order(ByteOrder.nativeOrder());
        byteOrder = buf.order();
    }

    public NetlinkMessage(ByteBuffer buf) {
        this.buf = buf;
        this.byteOrder = buf.order();
    }

    public ByteBuffer getBuffer() {
        return buf;
    }

    public byte getByte() {
        return buf.get();
    }

    public short getShort() {
        return buf.getShort();
    }

    public short getShort(ByteOrder order) {
        try {
            buf.order(order);
            return buf.getShort();
        } finally {
            buf.order(byteOrder);
        }
    }

    public int getInt() {
        return buf.getInt();
    }

    public int getInt(ByteOrder byteOrder) {
        try {
            buf.order(byteOrder);
            return buf.getInt();
        } finally {
            buf.order(this.byteOrder);
        }
    }

    public long getLong() {
        return buf.getLong();
    }

    public long getLong(ByteOrder byteOrder) {
        try {
            buf.order(byteOrder);
            return buf.getLong();
        } finally {
            buf.order(this.byteOrder);
        }
    }

    public int getInts(int[] bytes, ByteOrder newByteOrder) {
        try {
            buf.order(newByteOrder);
            return getInts(bytes);
        } finally {
            buf.order(this.byteOrder);
        }
    }

    public int getInts(int[] bytes) {
        for (int i = 0, bytesLength = bytes.length; i < bytesLength; i++) {
            bytes[i] = getInt();
        }

        return bytes.length;
    }

    public int getBytes(byte[] bytes) {
        buf.get(bytes);
        return bytes.length;
    }

    public boolean hasRemaining() {
        return buf.hasRemaining();
    }

    public Byte getAttrValueByte(AttrKey<Byte> attr) {
        return new SingleAttributeParser<Byte>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                data = buffer.get();
                return false;
            }
        }.parse(this);
    }

    public Short getAttrValueShort(AttrKey<Short> attr) {
        return new SingleAttributeParser<Short>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                data = buffer.getShort();
                return false;
            }
        }.parse(this);
    }

    public Integer getAttrValueInt(AttrKey<Integer> attr) {
        return new SingleAttributeParser<Integer>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                data = buffer.getInt();
                return false;
            }
        }.parse(this);
    }

    public Long getAttrValueLong(AttrKey<Long> attr) {
        return new SingleAttributeParser<Long>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                data = buffer.getLong();
                return false;
            }
        }.parse(this);
    }

    public Short getAttrValueShort(AttrKey<Short> attr, final ByteOrder order) {
        return new SingleAttributeParser<Short>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                try {
                    buffer.order(order);
                    data = buffer.getShort();
                } finally {
                    buffer.order(byteOrder);
                }

                return false;
            }
        }.parse(this);
    }

    public Integer getAttrValueInt(AttrKey<Integer> attr, final ByteOrder order) {
        return new SingleAttributeParser<Integer>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                try {
                    buffer.order(order);
                    data = buffer.getInt();
                } finally {
                    buffer.order(byteOrder);
                }

                return false;
            }
        }.parse(this);
    }

    public Long getAttrValueLong(AttrKey<Long> attr, final ByteOrder order) {
        return new SingleAttributeParser<Long>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                try {
                    buffer.order(order);
                    data = buffer.getLong();
                } finally {
                    buffer.order(byteOrder);
                }

                return false;
            }
        }.parse(this);
    }

    public String getAttrValueString(AttrKey<String> attr) {
        return new SingleAttributeParser<String>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buf.get(bytes);
                data = new String(bytes, 0, bytes.length - 1);
                return false;
            }
        }.parse(this);
    }

    public byte[] getAttrValueBytes(final AttrKey<byte[]> attr) {
       return new SingleAttributeParser<byte[]>(attr) {
           @Override
           protected boolean parseBuffer(ByteBuffer buffer) {
               data = new byte[buffer.remaining()];
               buffer.get(data);
               return false;
           }
       }.parse(this);
    }

    public interface CustomBuilder<T> {
        T newInstance(short type);
    }

    public <T extends Attr & BuilderAware> List<T> getAttrValue(AttrKey<List<T>> attr, final CustomBuilder<T> builder) {
        return new SingleAttributeParser<List<T>>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                final NetlinkMessage message = new NetlinkMessage(buffer.slice().order(buffer.order()));
                data = new ArrayList<T>();
                message.iterateAttributes(new AttributeParser() {
                    @Override
                    public boolean processAttribute(short attributeType, ByteBuffer buffer) {
                        T value = builder.newInstance(attributeType);

                        if (value != null) {
                            value.deserialize(message);
                            data.add(value);
                        }

                        return true;
                    }
                });

                return false;
            }
        }.parse(this);
    }

    public interface AttributeParser {
        /**
         * @param attributeType the type of attribute
         * @param buffer the buffer with the data
         *
         * @return true if the next attribute is to be parsed, false if not.
         */
        boolean processAttribute(short attributeType, ByteBuffer buffer);
    }

    public void iterateAttributes(AttributeParser attributeParser) {

        int limit = buf.limit();
        buf.mark();

        try {
            while (buf.hasRemaining()) {
                short   len         = buf.getShort();
                if ( len == 0 )
                    return;

                short   attrType    = (short) (buf.getShort() & NLA_TYPE_MASK);
                int     pos         = buf.position();

                buf.limit(pos + len - 4);

                if (!attributeParser.processAttribute(attrType, buf)) {
                    return;
                }

                buf.limit(limit);
                buf.position(pos + pad(len) - 4);
                buf.order(byteOrder);
            }
        } finally {
            buf.limit(limit);
            buf.order(byteOrder);
            buf.reset();
        }
    }


    public NetlinkMessage getAttrValueNested(AttrKey<NetlinkMessage> attr) {
        return new SingleAttributeParser<NetlinkMessage>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                data = new NetlinkMessage(buffer.slice().order(buffer.order()));
                return false;
            }
        }.parse(this);
    }

    /**
     * It will tell the instance to deserialize from the current object.
     *
     * @param attr the attribute name
     * @param instance the instance that will have to deserialize itself.
     * @param <T> the attribute type
     *
     * @return the updated instance if deserialization was successful of null if not
     */
    @Nullable
    public <T extends BuilderAware> T getAttrValue(AttrKey<? extends T> attr, final T instance) {
        return new SingleAttributeParser<T>(attr) {
            @Override
            protected boolean parseBuffer(ByteBuffer buffer) {
                final NetlinkMessage message = new NetlinkMessage(buffer.slice().order(buffer.order()));
                instance.deserialize(message);
                data = instance;
                return false;
            }
        }.parse(this);
    }

    public static void setAttrHeader(ByteBuffer buffer, short id, int len) {
        buffer.putShort((short) len);   // nla_len
        buffer.putShort(id);            // nla_type
    }

    public static int addAttribute(ByteBuffer buf, short id, String value) {

        int startPos = buf.position();
        int strLen = value.length() + 1;

        setAttrHeader(buf, id, 4 + strLen);

        // put the string
        buf.put(value.getBytes());
        buf.put((byte) 0);                  // put a null terminator

        // pad
        int padLen = pad(strLen);
        while (padLen != strLen) {
            buf.put((byte)0);
            padLen--;
        }

        return buf.position() - startPos;
    }

    public static int addAttribute(ByteBuffer buf, short id, byte[] value) {
        // save position
        int start = buf.position();

        // put a nl_attr header
        buf.putShort((short) 0);
        buf.putShort(id); // nla_type

        // write the message
        buf.put(value);

        // update the nl_attr length
        buf.putShort(start, (short) (buf.position() - start));

        return buf.position() - start;
    }

    public static Builder newMessageBuilder(int size, ByteOrder order) {
        return new Builder(size, order);
    }

    public static Builder newMessageBuilder(int size) {
        return new Builder(size, ByteOrder.nativeOrder());
    }

    public static Builder newMessageBuilder(ByteOrder order) {
        return newMessageBuilder(cLibrary.PAGE_SIZE, order);
    }

    public static Builder newMessageBuilder() {
        return newMessageBuilder(cLibrary.PAGE_SIZE);
    }

    public static int pad(int len) {
        int paddedLen = len & ~0x03;
        if ( paddedLen < len ) {
            paddedLen += 0x04;
        }

        return paddedLen;
    }

    private abstract static class SingleAttributeParser<T> implements AttributeParser {
        protected T data;
        AttrKey<? extends T> attr;

        protected SingleAttributeParser(AttrKey<? extends T> attr) {
            this.attr = attr;
        }

        @Override
        public boolean processAttribute(short attributeType, ByteBuffer buffer) {
            if ((attr.getId() & NLA_TYPE_MASK) != attributeType)
                return true;

            return parseBuffer(buffer);
        }

        protected abstract boolean parseBuffer(ByteBuffer buffer);

        public T parse(NetlinkMessage message) {
            message.iterateAttributes(this);
            return data;
        }
    }
}

