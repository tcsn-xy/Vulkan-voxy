#!/usr/bin/env python3
"""Compile/run data integration helpers with cached local dependencies and Java25."""
from pathlib import Path
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[3]
JAVA = Path(os.environ.get("JAVA_HOME") or subprocess.check_output(["/usr/libexec/java_home", "-v", "25"], text=True).strip()) / "bin"
CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"
OUTPUT = ROOT / "build/data-tests"
OUTPUT.mkdir(parents=True, exist_ok=True)

def version_key(version):
    return tuple(int(piece) if piece.isdigit() else piece for piece in re.split(r"([0-9]+)", version))

jars = []
for group in CACHE.iterdir():
    if not group.is_dir():
        continue
    for artifact in group.iterdir():
        if not artifact.is_dir():
            continue
        versions = sorted((v for v in artifact.iterdir() if v.is_dir()), key=lambda v: version_key(v.name), reverse=True)
        for version in versions[:1]:
            for jar in version.rglob("*.jar"):
                if not any(marker in jar.name for marker in ("-sources", "-javadoc", "natives-windows", "natives-linux", "natives-macos.jar")):
                    jars.append(jar)
merged = next((ROOT / ".gradle/loom-cache/minecraftMaven").rglob("minecraft-merged*26.3.jar"))
classpath = os.pathsep.join(map(str, [OUTPUT, ROOT / "build/classes/java/main", merged, *jars]))
files = [
    "common/util/ByteBudget.java", "common/util/DataMemoryBudget.java",
    "common/world/ColdSectionCodec.java", "common/world/LiveUpdateGuard.java", "common/world/StoredLighting.java",
    "common/world/WorldSection.java", "common/world/ActiveSectionTracker.java",
    "common/world/WorldEngine.java", "common/world/WorldUpdater.java", "common/world/SaveLoadSystem3.java",
    "commonImpl/importers/LimitedInputStream.java", "commonImpl/importers/RegionChunkReader.java",
    "commonImpl/importers/ImportedSkyLight.java", "commonImpl/importers/WorldImporter.java",
    "common/world/service/VoxelIngestService.java", "common/config/storage/rocksdb/RocksDBStorageBackend.java",
]
sources = [ROOT / "src/main/java/me/cortex/voxy" / f for f in files]
sources += [ROOT / "src/test/java/me/cortex/voxy/data" / f for f in ("DataBoundsTest.java", "DataCacheHarness.java", "DataStorageHarness.java")]
subprocess.run([str(JAVA / "javac"), "--release", "25", "-cp", classpath, "-d", str(OUTPUT), *map(str, sources)], check=True)
for name in ("DataBoundsTest", "DataCacheHarness", "DataStorageHarness"):
    subprocess.run([str(JAVA / "java"), ("-Xmx4G" if name == "DataStorageHarness" else "-Xmx128m"), "--enable-native-access=ALL-UNNAMED", "-cp", classpath, "me.cortex.voxy.common.world." + name], check=True)
