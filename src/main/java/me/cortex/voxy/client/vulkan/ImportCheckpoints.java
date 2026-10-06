package me.cortex.voxy.client.vulkan;
import com.google.gson.Gson;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.cortex.voxy.commonImpl.importers.WorldImporter;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.io.File;
import java.util.*;
import java.security.MessageDigest;
/** Restartable directory imports; state is stored in the target's cache, never in the source. */
public final class ImportCheckpoints {
    private record Saved(String source,String fingerprint,int region,int chunk,boolean paused,boolean refresh,List<ImportCheckpointPolicy.Stamp> files){}
    private record State(Path file,Path source,String fingerprint,int regionCount,List<ImportCheckpointPolicy.Stamp> files){}
    private static final Gson JSON=new Gson();
    private static final Map<WorldImporter,State> states=Collections.synchronizedMap(new WeakHashMap<>());
    private static String hash(String text){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static Path refreshBackupPath(me.cortex.voxy.common.world.WorldEngine engine){
        return Minecraft.getInstance().gameDirectory.toPath().resolve(".voxy-refresh-backups").resolve(WorldIdentifier.of(Minecraft.getInstance().level).getWorldId()).resolve(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+"-"+UUID.randomUUID()).resolve("database");
    }
    public static void prepare(WorldImporter importer,File directory){
        Path source=directory.toPath().toAbsolutePath().normalize();
        File[] files=directory.listFiles((d,n)->n.matches("r\\.-?[0-9]+\\.-?[0-9]+\\.mca"));if(files==null)throw new IllegalArgumentException("Unreadable import source");Arrays.sort(files,File::compareTo);
        var stamps=new ArrayList<ImportCheckpointPolicy.Stamp>();for(var f:files)stamps.add(new ImportCheckpointPolicy.Stamp(f.getName(),f.length(),f.lastModified()));
        StringBuilder snapshot=new StringBuilder();for(var f:files)snapshot.append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified()).append('\n');String fingerprint=hash(snapshot.toString());
        var mc=Minecraft.getInstance();var identifier=WorldIdentifier.of(mc.level);
        Path file=mc.gameDirectory.toPath().resolve(".voxy-import-state").resolve(identifier.getWorldId()+"-"+hash(source.toString())+(importer.isCurrentWorldRefresh()?"-refresh":"")+".json");
        var cursor=new WorldImporter.ImportCursor(0,0);
        try{if(Files.isRegularFile(file)){
            Saved saved=JSON.fromJson(Files.readString(file),Saved.class);
            if(saved.source().equals(source.toString())&&saved.refresh()==importer.isCurrentWorldRefresh()){
                if(saved.fingerprint().equals(fingerprint))cursor=new WorldImporter.ImportCursor(saved.region(),saved.chunk());
                else if(importer.isCurrentWorldRefresh()&&saved.files()!=null){var resumed=ImportCheckpointPolicy.resume(saved.files(),stamps,saved.region(),saved.chunk());cursor=new WorldImporter.ImportCursor(resumed.region(),resumed.chunk());}
            }
        }}catch(Exception e){Logger.warn("Ignoring unusable Voxy import checkpoint",e);}
        states.put(importer,new State(file,source,fingerprint,files.length,List.copyOf(stamps)));
        importer.importRegionDirectoryAsync(directory,cursor);
    }
    public static synchronized void save(WorldImporter importer){
        var s=states.get(importer);if(s==null)return;var c=importer.getCursor();
        try{Files.createDirectories(s.file().getParent());Path tmp=s.file().resolveSibling(s.file().getFileName()+".tmp");Files.writeString(tmp,JSON.toJson(new Saved(s.source().toString(),s.fingerprint(),c.regionIndex(),c.chunkIndex(),importer.isPaused(),importer.isCurrentWorldRefresh(),s.files())));try{Files.move(tmp,s.file(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,s.file(),StandardCopyOption.REPLACE_EXISTING);}}catch(java.io.IOException e){Logger.warn("Cannot save Voxy import checkpoint",e);}
    }
    public static synchronized void completed(WorldImporter importer){
        var s=states.get(importer);if(s==null)return;
        if(importer.getFailure()==null&&importer.getCursor().regionIndex()>=s.regionCount()){try{Files.deleteIfExists(s.file());}catch(java.io.IOException e){Logger.warn("Cannot remove completed checkpoint",e);}}
        else save(importer);
        states.remove(importer);
    }
}
