package proto;

public interface Op {
    // lets define every single type of packet the server can handle
    // TODO: maybe transfer this to client aswell, or pack a helper class for this shit
    byte HEARTBEAT          = 0x01;
    byte HEARTBEAT_ACK      = 0x11;

    byte CREATE_ROOM        = 0x02;
    byte CREATE_ROOM_ACK    = 0x12; // server -> client

    byte JOIN_ROOM          = 0x03;
    byte JOIN_ROOM_ACK      = 0x13;

    byte CHAT_SEND          = 0x04;
    byte CHAT_BROADCAST     = 0x14; // server fanout

    byte LEAVE              = 0x05;
    byte LEAVE_ACK          = 0x15;

    byte ERROR              = 0x7F;
}
