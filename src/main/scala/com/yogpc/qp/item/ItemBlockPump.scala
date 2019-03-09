/*
 * Copyright (C) 2012,2013 yogpstop This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package com.yogpc.qp.item

import com.yogpc.qp.item.IEnchantableItem.{FORTUNE, SILKTOUCH, UNBREAKING}
import net.minecraft.block.Block
import net.minecraft.enchantment.{Enchantment, EnchantmentHelper}
import net.minecraft.init.Enchantments
import net.minecraft.item.{ItemBlock, ItemStack}

class ItemBlockPump(b: Block) extends ItemBlock(b) with IEnchantableItem {
  override def canMove(is: ItemStack, enchantment: Enchantment): Boolean = {
    if (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, is) > 0) {
      FORTUNE.negate() and (UNBREAKING or SILKTOUCH)
    } else if (EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, is) > 0) {
      SILKTOUCH.negate() and (UNBREAKING or FORTUNE)
    } else {
      SILKTOUCH or FORTUNE or UNBREAKING
    }
  }.test(enchantment)

  override def isBookEnchantable(s1: ItemStack, s2: ItemStack) = false
}
