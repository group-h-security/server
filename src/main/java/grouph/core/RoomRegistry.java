package grouph.core;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// stores multiple rooms in a thread safe way
public final class RoomRegistry {
    private final java.util.Map<UUID, Room> rooms = new ConcurrentHashMap<>();
    private final java.util.Map<String, UUID> codeIndex = new ConcurrentHashMap<>();

    // create room by code
    public Room getOrCreateByCode(String code) {
        UUID id = codeIndex.computeIfAbsent(code, c -> UUID.randomUUID());
        return rooms.computeIfAbsent(id, uuid -> new Room(uuid, code));
    }

    // get by room code
    public Room getByCode(String code) {
        UUID id = codeIndex.get(code);
        return id == null ? null : rooms.get(id);
    }

    // get room by id
    public Room getById(UUID id) { return rooms.get(id); }

    // check if the room given has no users if so remove
    public void removeIfEmpty(Room room) {
        if (room == null) return;
        if (room.isEmpty()) {
            rooms.remove(room.roomId);
            codeIndex.remove(room.roomCode);
        }
    }
}
