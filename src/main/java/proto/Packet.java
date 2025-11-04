package proto;

public class Packet {
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

    // ill put these in an interface called Op
}

