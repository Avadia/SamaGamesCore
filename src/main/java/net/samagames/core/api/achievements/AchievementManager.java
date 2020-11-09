package net.samagames.core.api.achievements;

import com.google.common.base.Preconditions;
import net.samagames.api.achievements.*;
import net.samagames.api.exceptions.DataNotFoundException;
import net.samagames.core.ApiImplementation;
import net.samagames.core.api.player.PlayerData;
import net.samagames.persistanceapi.beans.achievements.AchievementBean;
import net.samagames.persistanceapi.beans.achievements.AchievementCategoryBean;
import net.samagames.persistanceapi.beans.achievements.AchievementProgressBean;
import net.samagames.tools.PersistanceUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/*
 * This file is part of SamaGamesCore.
 *
 * SamaGamesCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SamaGamesCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SamaGamesCore.  If not, see <http://www.gnu.org/licenses/>.
 */
public class AchievementManager implements IAchievementManager {
    private final ApiImplementation api;
    private Achievement[] achievementsCache;
    private AchievementCategory[] achievementCategoriesCache;

    public AchievementManager(ApiImplementation api) {
        this.api = api;
        this.achievementsCache = new Achievement[0];
        this.achievementCategoriesCache = new AchievementCategory[0];

        api.getPlugin().getExecutor().schedule(() ->
        {
            try {
                List<AchievementCategoryBean> categoryBeanList = api.getGameServiceManager().getAchievementCategories();
                List<AchievementCategory> categories = new ArrayList<>();

                categoryBeanList.forEach(achievementCategoryBean -> categories.add(new AchievementCategory(achievementCategoryBean.getCategoryId(), achievementCategoryBean.getCategoryName(), PersistanceUtils.makeStack(this.api.getPlugin(), achievementCategoryBean.getItemMinecraftId(), achievementCategoryBean.getCategoryName(), achievementCategoryBean.getCategoryDescription()), achievementCategoryBean.getCategoryDescription().split("/n"), achievementCategoryBean.getParentId() < categories.size() && achievementCategoryBean.getParentId() >= 0 ? categories.get(achievementCategoryBean.getParentId()) : null)));

                List<AchievementBean> allAchievements = api.getGameServiceManager().getAchievements();
                int n = allAchievements.size();
                int n2 = categoryBeanList.size();

                Achievement[] achievementsCache = new Achievement[n == 0 ? 0 : Math.max(n, allAchievements.get(n - 1).getAchievementId())];

                for (AchievementBean bean : allAchievements) {
                    AchievementCategory category = categories.stream().filter(achievementCategory -> achievementCategory.getID() == bean.getCategoryId()).findFirst().orElse(null);

                    if (bean.getProgressTarget() == 1)
                        achievementsCache[bean.getAchievementId() - 1] = new Achievement(bean.getAchievementId(), bean.getAchievementName(), category, bean.getAchievementDescription().split("/n"));
                    else
                        achievementsCache[bean.getAchievementId() - 1] = new IncrementationAchievement(bean.getAchievementId(), bean.getAchievementName(), category, bean.getAchievementDescription().split("/n"), bean.getProgressTarget());
                }

                AchievementCategory[] achievementCategoriesCache = new AchievementCategory[n2 == 0 ? 0 : Math.max(n2, categories.get(n2 - 1).getID())];
                categories.forEach(achievementCategory -> achievementCategoriesCache[achievementCategory.getID()] = achievementCategory);

                this.achievementsCache = achievementsCache;//Avoid concurrent errors using temporary arrays
                this.achievementCategoriesCache = achievementCategoriesCache;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, TimeUnit.MINUTES);
    }

    public void loadPlayer(UUID uuid) {
        try {
            PlayerData playerData = this.api.getPlayerManager().getPlayerData(uuid);
            List<AchievementProgressBean> list = this.api.getGameServiceManager().getAchievementProgresses(playerData.getPlayerBean());
            list.forEach(bean ->
            {
                try {
                    this.getAchievementByID(bean.getAchievementId()).addProgress(uuid, bean.getProgressId(), bean.getProgress(), bean.getStartDate(), bean.getUnlockDate());
                } catch (DataNotFoundException e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void unloadPlayer(UUID player) {
        for (Achievement achievement : this.achievementsCache) {
            AchievementProgress progress = achievement.getProgress(player);

            if (progress == null || !progress.isChanged())
                continue;

            AchievementProgressBean bean = new AchievementProgressBean(progress.getProgressId(), achievement.getID(), progress.getProgress(), progress.getStartTime(), progress.getUnlockTime(), player);

            try {
                if (progress.getProgressId() == -1)
                    this.api.getGameServiceManager().createAchievementProgress(this.api.getPlayerManager().getPlayerData(player).getPlayerBean(), bean);
                else
                    this.api.getGameServiceManager().updateAchievementProgress(bean);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void incrementAchievement(UUID uuid, IncrementationAchievement incrementationAchievement, int amount) {
        incrementationAchievement.increment(uuid, amount);
    }

    @Override
    public void incrementAchievement(UUID uuid, int id, int amount) throws DataNotFoundException {
        Achievement achievement = this.getAchievementByID(id);
        if (achievement instanceof IncrementationAchievement)
            ((IncrementationAchievement) achievement).increment(uuid, amount);
        else
            throw new IllegalArgumentException("Achievement is not incrementable");
    }

    @Override
    public void incrementAchievements(UUID uuid, int[] ids, int amount) throws DataNotFoundException {
        for (int id : ids)
            this.incrementAchievement(uuid, id, amount);
    }

    @Override
    public Achievement getAchievementByID(int id) throws DataNotFoundException {
        for (Achievement achievement : this.achievementsCache)
            if (achievement.getID() == id)
                return achievement;

        throw new DataNotFoundException("Achievement with id " + id + " not found");
    }

    @Override
    public AchievementCategory getAchievementCategoryByID(int id) throws DataNotFoundException {
        for (AchievementCategory achievementCategory : this.achievementCategoriesCache)
            if (achievementCategory.getID() == id)
                return achievementCategory;

        throw new DataNotFoundException("AchievementCategory with id " + id + " not found");
    }

    @Override
    public List<Achievement> getAchievements() {
        return Arrays.asList(this.achievementsCache);
    }

    @Override
    public List<AchievementCategory> getAchievementsCategories() {
        return Arrays.asList(this.achievementCategoriesCache);
    }

    @Override
    public boolean isUnlocked(UUID uuid, Achievement achievement) {
        return achievement.isUnlocked(uuid);
    }

    @Override
    public boolean isUnlocked(UUID uuid, int id) throws DataNotFoundException {
        Achievement achievement = this.getAchievementByID(id);
        Preconditions.checkNotNull(achievement, "Achievement with id " + id + " not found");
        return achievement.isUnlocked(uuid);
    }
}