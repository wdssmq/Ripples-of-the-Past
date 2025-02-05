package com.github.standobyte.jojo.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

import io.netty.handler.codec.DecoderException;
import net.minecraft.client.network.play.IClientPlayNetHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.extensions.IForgePacketBuffer;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.RegistryManager;

public class NetworkUtil {

    public static void broadcastWithCondition(List<ServerPlayerEntity> players, @Nullable PlayerEntity clientHandled, 
            double x, double y, double z, double radius, RegistryKey<World> dimension, IPacket<IClientPlayNetHandler> packet, 
            Predicate<PlayerEntity> condition) {
        for (int i = 0; i < players.size(); ++i) {
            ServerPlayerEntity player = players.get(i);
            if (player != clientHandled && player.level.dimension() == dimension && condition.test(player)) {
                double d0 = x - player.getX();
                double d1 = y - player.getY();
                double d2 = z - player.getZ();
                if (d0 * d0 + d1 * d1 + d2 * d2 < radius * radius) {
                    player.connection.send(packet);
                }
            }
        }
    }
    
    
    
    public static <T extends IForgeRegistryEntry<T>> void writeRegistryIds(IForgePacketBuffer buf, @Nonnull List<T> entries) {
        Objects.requireNonNull(entries, "Cannot write a null registry entries list!");
        buf.getBuffer().writeBoolean(!entries.isEmpty());
        if (entries.isEmpty()) return;
        IForgeRegistry<T> retrievedRegistry = null;
        for (T entry : entries) {
            Class<T> entryRegType = Objects.requireNonNull(entry, "Cannot write a null registry entry!").getRegistryType();
            IForgeRegistry<T> entryRegistry = RegistryManager.ACTIVE.getRegistry(entryRegType);
            Preconditions.checkArgument(entryRegistry != null, "Cannot write registry id for an unknown registry type: %s", entryRegType.getName());
            if (retrievedRegistry == null) retrievedRegistry = entryRegistry;
            Preconditions.checkArgument(retrievedRegistry == entryRegistry, "Cannot write entries of different registry types: %s, %s", 
                    retrievedRegistry.getRegistrySuperType().getName(), entryRegType.getName());
            Preconditions.checkArgument(retrievedRegistry.containsValue(entry), "Cannot find %s in %s", 
                    entry.getRegistryName() != null ? entry.getRegistryName() : entry, retrievedRegistry.getRegistryName());
        }
        ResourceLocation name = retrievedRegistry.getRegistryName();
        ForgeRegistry<T> reg = (ForgeRegistry<T>) retrievedRegistry;
        buf.getBuffer().writeResourceLocation(name);
        buf.getBuffer().writeVarInt(entries.size());
        for (T entry : entries) {
            buf.getBuffer().writeVarInt(reg.getID(entry));
        }
    }

    public static <T extends IForgeRegistryEntry<T>> List<T> readRegistryIds(IForgePacketBuffer buf) {
        if (!buf.getBuffer().readBoolean()) return Collections.emptyList();
        ResourceLocation location = buf.getBuffer().readResourceLocation();
        ForgeRegistry<T> registry = RegistryManager.ACTIVE.getRegistry(location);
        int size = buf.getBuffer().readVarInt();
        List<T> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(registry.getValue(buf.getBuffer().readVarInt()));
        }
        return entries;
    }

    public static <T extends IForgeRegistryEntry<T>> List<T> readRegistryIdsSafe(IForgePacketBuffer buf, Class<? super T> registrySuperType) {
        List<T> values = readRegistryIds(buf);
        for (T value : values) {
            if (!value.getRegistryType().equals(registrySuperType))
                throw new IllegalArgumentException("Attempted to read an registryValue of the wrong type from the Buffer!");
        }
        return values;
    }
    
    
    public static PacketBuffer writeFloatArray(PacketBuffer buf, float[] arr) {
        buf.writeVarInt(arr.length);
        for (float num : arr) {
            buf.writeFloat(num);
        }
        return buf;
    }
    
    public static float[] readFloatArray(PacketBuffer buf) {
        return readFloatArray(buf, buf.readableBytes() / 4);
    }

    public static float[] readFloatArray(PacketBuffer buf, int maxAllowed) {
        int n = buf.readVarInt();
        if (n > maxAllowed) {
            throw new DecoderException("FloatArray with size " + n + " is bigger than allowed " + maxAllowed);
        } else {
            float[] arr = new float[n];
            for (int i = 0; i < n; i++) {
                arr[i] = buf.readFloat();
            }
            return arr;
        }
    }

    public static PacketBuffer writeIntArray(PacketBuffer buf, int[] arr) {
        buf.writeVarInt(arr.length);

        for (int i : arr) {
            buf.writeInt(i);
        }

        return buf;
    }

    public static int[] readIntArray(PacketBuffer buf) {
        return readIntArray(buf, buf.readableBytes());
    }

    public static int[] readIntArray(PacketBuffer buf, int maxAllowed) {
        int n = buf.readVarInt();
        if (n > maxAllowed) {
            throw new DecoderException("IntArray with size " + n + " is bigger than allowed " + maxAllowed);
        } else {
            int[] arr = new int[n];

            for(int i = 0; i < arr.length; ++i) {
                arr[i] = buf.readInt();
            }

            return arr;
        }
    }
    
    public static void writeVecApproximate(PacketBuffer buf, Vector3d vec) {
        buf.writeInt((int) (vec.x * 8.0));
        buf.writeInt((int) (vec.y * 8.0));
        buf.writeInt((int) (vec.z * 8.0));
    }
    
    public static Vector3d readVecApproximate(PacketBuffer buf) {
        return new Vector3d(
                buf.readInt() / 8.0, 
                buf.readInt() / 8.0, 
                buf.readInt() / 8.0);
    }
    
    public static <T> void writeOptionally(PacketBuffer buf, @Nullable T obj, BiConsumer<PacketBuffer, T> write) {
        buf.writeBoolean(obj != null);
        if (obj != null) {
            write.accept(buf, obj);
        }
    }
    
    public static <T> void writeOptional(PacketBuffer buf, @Nonnull Optional<T> objOptional, BiConsumer<PacketBuffer, T> write) {
        buf.writeBoolean(objOptional.isPresent());
        objOptional.ifPresent(obj -> write.accept(buf, obj));
    }
    
    public static <T> Optional<T> readOptional(PacketBuffer buf, Function<PacketBuffer, T> read) {
        return buf.readBoolean() ? Optional.of(read.apply(buf)) : Optional.empty();
    }
    
    public static <T> int writeCollection(PacketBuffer buf, Collection<T> collection, BiConsumer<PacketBuffer, T> writeElement, 
            boolean removeWrittenFromCollection) {
        int i = 0;
        int initialWriterIndex = buf.writerIndex();
        buf.writeInt(0);
        
        int lastWriterIndex = initialWriterIndex;
        int maxElemSize = 0;
        Iterator<T> iter = collection.iterator();
        while (iter.hasNext()) {
            if (buf.capacity() < maxElemSize) break;
            T element = iter.next();
            writeElement.accept(buf, element);
            i++;
            if (removeWrittenFromCollection) {
                iter.remove();
            }
            int writerIndex = buf.writerIndex();
            maxElemSize = Math.max(maxElemSize, writerIndex - lastWriterIndex);
            lastWriterIndex = writerIndex;
        }
                
        buf.setInt(initialWriterIndex, i);
        return i;
    }
    
    public static <T> List<T> readCollection(PacketBuffer buf, Function<PacketBuffer, T> readElement) {
        int size = buf.readInt();
        if (size > 0) {
            List<T> list = new ArrayList<T>(size);
            for (int i = 0; i < size; i++) {
                list.add(readElement.apply(buf));
            }
            return list;
        }
        return Collections.emptyList();
    }
}
