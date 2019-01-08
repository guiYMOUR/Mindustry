package io.anuke.mindustry.world.blocks.storage;

import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.arc.Core;
import io.anuke.arc.collection.EnumSet;
import io.anuke.arc.entities.Effects;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.Lines;
import io.anuke.arc.graphics.g2d.TextureRegion;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.Time;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.content.Fx;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.entities.traits.SpawnerTrait;
import io.anuke.mindustry.entities.units.BaseUnit;
import io.anuke.mindustry.entities.units.UnitType;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.maps.TutorialSector;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockFlag;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.*;

public class CoreBlock extends StorageBlock{
    protected TextureRegion openRegion;
    protected TextureRegion topRegion;

    public CoreBlock(String name){
        super(name);

        solid = false;
        solidifes = true;
        update = true;
        size = 3;
        hasItems = true;
        viewRange = 200f;
        flags = EnumSet.of(BlockFlag.resupplyPoint, BlockFlag.target);
    }

    @Remote(called = Loc.server)
    public static void onUnitRespawn(Tile tile, Unit player){
        if(player == null || tile.entity == null) return;

        CoreEntity entity = tile.entity();
        Effects.effect(Fx.spawn, entity);
        entity.solid = false;
        entity.progress = 0;
        entity.currentUnit = player;
        entity.currentUnit.heal();
        entity.currentUnit.rotation = 90f;
        entity.currentUnit.setNet(tile.drawx(), tile.drawy());
        entity.currentUnit.add();
        entity.currentUnit = null;

        if(player instanceof Player){
            ((Player) player).endRespawning();
        }
    }

    @Remote(called = Loc.server)
    public static void setCoreSolid(Tile tile, boolean solid){
        if(tile == null) return;
        CoreEntity entity = tile.entity();
        if(entity != null) entity.solid = solid;
    }

    @Override
    public int getMaximumAccepted(Tile tile, Item item){
        return itemCapacity * state.teams.get(tile.getTeam()).cores.size;
    }

    @Override
    public void onProximityUpdate(Tile tile) {
        for(Tile other : state.teams.get(tile.getTeam()).cores){
            if(other != tile){
                tile.entity.items = other.entity.items;
            }
        }
        state.teams.get(tile.getTeam()).cores.add(tile);
    }

    @Override
    public boolean canBreak(Tile tile){
        return state.teams.get(tile.getTeam()).cores.size > 1;
    }

    @Override
    public void removed(Tile tile){
        state.teams.get(tile.getTeam()).cores.remove(tile);

        int max = itemCapacity * state.teams.get(tile.getTeam()).cores.size;
        for(Item item : content.items()){
            tile.entity.items.set(item, Math.min(tile.entity.items.get(item), max));
        }
    }

    @Override
    public void placed(Tile tile){
        state.teams.get(tile.getTeam()).cores.add(tile);
    }

    @Override
    public void load(){
        super.load();

        openRegion = Core.atlas.find(name + "-open");
        topRegion = Core.atlas.find(name + "-top");
    }

    @Override
    public void draw(Tile tile){
        CoreEntity entity = tile.entity();

        Draw.rect(entity.solid ? Core.atlas.find(name) : openRegion, tile.drawx(), tile.drawy());

        Draw.alpha(entity.heat);
        Draw.rect(topRegion, tile.drawx(), tile.drawy());
        Draw.color();

        if(entity.currentUnit != null){
            Unit player = entity.currentUnit;

            TextureRegion region = player.getIconRegion();

            Shaders.build.region = region;
            Shaders.build.progress = entity.progress;
            Shaders.build.color.set(Palette.accent);
            Shaders.build.time = -entity.time / 10f;

            Draw.shader(Shaders.build, true);
            Draw.rect(region, tile.drawx(), tile.drawy());
            Draw.shader();

            Draw.color(Palette.accent);

            Lines.lineAngleCenter(
                    tile.drawx() + Mathf.sin(entity.time, 6f, Vars.tilesize / 3f * size),
                    tile.drawy(),
                    90,
                    size * Vars.tilesize / 2f);

            Draw.reset();
        }
    }

    @Override
    public boolean isSolidFor(Tile tile){
        CoreEntity entity = tile.entity();

        return entity.solid;
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        if(Net.server() || !Net.active()) super.handleItem(item, tile, source);
    }

    @Override
    public void update(Tile tile){
        CoreEntity entity = tile.entity();

        if(!entity.solid && !Units.anyEntities(tile)){
            Call.setCoreSolid(tile, true);
        }

        if(entity.currentUnit != null){
            if(!entity.currentUnit.isDead()){
                entity.currentUnit = null;
                return;
            }
            entity.heat = Mathf.lerpDelta(entity.heat, 1f, 0.1f);
            entity.time += entity.delta();
            entity.progress += 1f / state.mode.respawnTime * entity.delta();

            if(entity.progress >= 1f){
                Call.onUnitRespawn(tile, entity.currentUnit);
            }
        }
    }

    @Override
    public TileEntity newEntity(){
        return new CoreEntity();
    }

    public class CoreEntity extends TileEntity implements SpawnerTrait{
        public Unit currentUnit;
        boolean solid = true;
        float progress;
        float time;
        float heat;

        @Override
        public void updateSpawning(Unit unit){
            if(!netServer.isWaitingForPlayers() && currentUnit == null){
                currentUnit = unit;
                progress = 0f;
                unit.set(tile.drawx(), tile.drawy());
            }
        }

        @Override
        public float getSpawnProgress(){
            return progress;
        }

        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeBoolean(solid);
        }

        @Override
        public void read(DataInput stream) throws IOException{
            solid = stream.readBoolean();
        }
    }
}
