package proto;

// okay so knowing how packets are send using TCP i have a general idea about how this should look
// each packet should have a magic header, this ensures that we are actually sending a valid packet,
// just like how an executable file like MachO has a 4 (i think) byte magic number at the top, denoting
// that it is indeed a MachO executable file

// so along with this each packet represents an action, some of which should absolutely include
// we can denote the following with one byte, so header then opcode
// HEARTBEAT: ensure user is alive -> 0x01
// CREATE_ROOM: create a room, give back roomCode -> 0x02
// JOIN_ROOM: given a room code, join that room -> 0x03
// CHAT_SEND: obvious -> 0x04
// LEAVE: obvious -> 0x05
// HEARTBEAT_ACK: acknowledge the server -> 0x01

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

// ill put these in an interface called Op
public class Packet {
    public static final int MAGIC = 0x43484154; // "CHAT"
    public static final int HEADER_SIZE = 12; // header is 12 bytes

    public byte opcode; // the action of our packet
    public byte version = 1; // this is a good idea if protocol changes

    // ordered list of TLV's that make up payload
    public final List<Tlv> tlvs = new ArrayList<>();

    // encoding, we should be able to serialise a packet into a byte array ready to write to a socket
    public byte[] toBytes() {

    }

    // TLV subclass
    public static final class Tlv {
        // this should be a short, 16 bit unsigned
        public final int type; // the actual field id, eg USERNAME=0x0001
        public final byte[] value; // the value of the field, we can interpret this how we want, String, int whatever

        public Tlv(int type, byte[] value) {
            this.type = type;
            this.value = value;
        }

        // number of bytes the Tlv will occupy
        public int size() {
            return 2 + 2 + value.length; // each tlv header is 4 bytes + the value length
        }

        // okay so ByteBuffers have more convinient methods to deal with byte arrays
        public void writeTo(ByteBuffer b) {
            b.putShort((short) type);
            b.putShort((short) value.length);
            b.put(value);
        }


    }

}




