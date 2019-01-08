package io.anuke.mindustry.world.blocks.defense.turrets;

import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.entities.Effects;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.entities.bullet.BulletType;
import io.anuke.mindustry.entities.effect.Fire;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.values.LiquidFilterValue;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static io.anuke.mindustry.Vars.tilesize;
import static io.anuke.mindustry.Vars.world;

public abstract class LiquidTurret extends Turret{
    protected ObjectMap<Liquid, BulletType> ammo = new ObjectMap<>();

    public LiquidTurret(String name){
        super(name);
        hasLiquids = true;
    }

    /**Initializes accepted ammo map. Format: [liquid1, bullet1, liquid2, bullet2...]*/
    protected void ammo(Object... objects){
        ammo = ObjectMap.of(objects);
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.inputLiquid, new LiquidFilterValue(item -> ammo.containsKey(item)));
    }

    @Override
    protected boolean validateTarget(Tile tile) {
        TurretEntity entity = tile.entity();
        if(entity.liquids.current().canExtinguish() && entity.target instanceof Tile){
            return Fire.has(((Tile) entity.target).x, ((Tile) entity.target).y);
        }
        return super.validateTarget(tile);
    }

    @Override
    protected void findTarget(Tile tile) {
        TurretEntity entity = tile.entity();
        if(entity.liquids.current().canExtinguish()){
            int tr = (int)(range / tilesize);
            for (int x = -tr; x <= tr; x++) {
                for (int y = -tr; y <= tr; y++) {
                    if(Fire.has(x + tile.x, y + tile.y)){
                        entity.target = world.tile(x + tile.x, y + tile.y);
                        return;
                    }
                }
            }
        }

        super.findTarget(tile);
    }

    @Override
    protected void effects(Tile tile){
        BulletType type = peekAmmo(tile);

        TurretEntity entity = tile.entity();

        Effects.effect(type.shootEffect, entity.liquids.current().color, tile.drawx() + tr.x, tile.drawy() + tr.y, entity.rotation);
        Effects.effect(type.smokeEffect, entity.liquids.current().color, tile.drawx() + tr.x, tile.drawy() + tr.y, entity.rotation);

        if(shootShake > 0){
            Effects.shake(shootShake, shootShake, tile.entity);
        }

        entity.recoil = recoil;
    }

    @Override
    public BulletType useAmmo(Tile tile){
        TurretEntity entity = tile.entity();
        if(tile.isEnemyCheat()) return ammo.get(entity.liquids.current());
        BulletType type = ammo.get(entity.liquids.current());
        entity.liquids.remove(entity.liquids.current(), type.ammoMultiplier);
        return type;
    }

    @Override
    public BulletType peekAmmo(Tile tile){
        return ammo.get(tile.entity.liquids.current());
    }

    @Override
    public boolean hasAmmo(Tile tile){
        TurretEntity entity = tile.entity();
        return ammo.get(entity.liquids.current()) != null && entity.liquids.total() >= ammo.get(entity.liquids.current()).ammoMultiplier;
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return false;
    }

    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount){
        return super.acceptLiquid(tile, source, liquid, amount) && ammo.get(liquid) != null
                && (tile.entity.liquids.current() == liquid || (ammo.containsKey(tile.entity.liquids.current()) && tile.entity.liquids.get(tile.entity.liquids.current()) <= ammo.get(tile.entity.liquids.current()).ammoMultiplier + 0.001f));
    }

    public class LiquidTurretEntity extends TurretEntity{
        @Override
        public void write(DataOutput stream) throws IOException{
            stream.writeByte(ammo.size);
            for(AmmoEntry entry : ammo){
                LiquidEntry i = (LiquidEntry)entry;
                stream.writeByte(i.liquid.id);
                stream.writeShort(i.amount);
            }
        }

        @Override
        public void read(DataInput stream) throws IOException{
            byte amount = stream.readByte();
            for(int i = 0; i < amount; i++){
                Liquid liquid = Vars.content.liquid(stream.readByte());
                short a = stream.readShort();
                totalAmmo += a;
                ammo.add(new LiquidEntry(liquid, a));
            }
        }
    }

    class LiquidEntry extends AmmoEntry{
        protected Liquid liquid;

        LiquidEntry(Liquid liquid, int amount){
            this.liquid = liquid;
            this.amount = amount;
        }

        @Override
        public BulletType type(){
            return ammo.get(liquid);
        }
    }

}
