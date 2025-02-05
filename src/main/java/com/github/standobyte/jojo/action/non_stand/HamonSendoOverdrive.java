package com.github.standobyte.jojo.action.non_stand;

import java.util.List;
import java.util.Random;

import com.github.standobyte.jojo.action.ActionTarget;
import com.github.standobyte.jojo.init.ModNonStandPowers;
import com.github.standobyte.jojo.power.nonstand.INonStandPower;
import com.github.standobyte.jojo.power.nonstand.type.HamonData;
import com.github.standobyte.jojo.power.nonstand.type.HamonPowerType;
import com.github.standobyte.jojo.power.nonstand.type.HamonSkill.HamonStat;
import com.github.standobyte.jojo.util.damage.DamageUtil;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

public class HamonSendoOverdrive extends HamonAction {

    public HamonSendoOverdrive(HamonAction.Builder builder) {
        super(builder);
    }
    
    @Override
    protected void perform(World world, LivingEntity user, INonStandPower power, ActionTarget target) {
        if (!world.isClientSide()) {
            HamonData hamon = power.getTypeSpecificData(ModNonStandPowers.HAMON.get()).get();
            BlockPos pos = target.getBlockPos();
            Direction face = target.getFace();
            double diameter = 2 + (double) (hamon.getHamonStrengthLevel() * 6) / (double) HamonData.MAX_STAT_LEVEL
                    * hamon.getBloodstreamEfficiency();
            double radiusMinus1 = (diameter - 1) / 2;
            AxisAlignedBB aabb = new AxisAlignedBB(pos).inflate(radiusMinus1).expandTowards(Vector3d.atLowerCornerOf(face.getNormal()))
                    .move(Vector3d.atLowerCornerOf(face.getNormal()).scale(-radiusMinus1));
            Random random = user.getRandom();
            int sparksCount = Math.max(MathHelper.floor(diameter * diameter * diameter / 16), 1);
            for (int i = 0; i < sparksCount; i++) {
                HamonPowerType.createHamonSparkParticles(world, null, 
                        aabb.minX + random.nextDouble() * (aabb.maxX - aabb.minX), 
                        aabb.minY + random.nextDouble() * (aabb.maxY - aabb.minY), 
                        aabb.minZ + random.nextDouble() * (aabb.maxZ - aabb.minZ), 
                        0.1F);
            }
            List<Entity> entities = world.getEntitiesOfClass(LivingEntity.class, aabb, EntityPredicates.NO_CREATIVE_OR_SPECTATOR);
            boolean givePoints = false;
            for (Entity entity : entities) {
                if (!entity.is(user) && DamageUtil.dealHamonDamage(entity, 0.25F, user, null)) {
                    givePoints = true;
                }
            }
            if (givePoints) {
                hamon.hamonPointsFromAction(HamonStat.STRENGTH, getEnergyCost(power));
            }
        }
    }
    
    @Override
    public TargetRequirement getTargetRequirement() {
        return TargetRequirement.BLOCK;
    }
}
