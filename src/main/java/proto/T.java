package proto;

// this allows us to reorder fields like username, roomcode, message
// packet might have header, including magic, with an opcode, then the first field might be username
// which we would denote with a T of maybe 0x0001
// i believe this is known as TLV, an encoding scheme https://en.wikipedia.org/wiki/Type%E2%80%93length%E2%80%93value
public interface T {
    // TODO: major major
}
