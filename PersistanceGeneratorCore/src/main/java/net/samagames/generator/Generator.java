package net.samagames.generator;

import com.squareup.javapoet.*;
import net.samagames.api.games.GamesNames;
import net.samagames.api.stats.IPlayerStats;
import net.samagames.persistanceapi.beans.players.GroupsBean;
import net.samagames.persistanceapi.beans.players.PlayerBean;
import net.samagames.persistanceapi.beans.players.PlayerSettingsBean;
import net.samagames.persistanceapi.beans.shop.ItemDescriptionBean;
import net.samagames.persistanceapi.beans.shop.TransactionBean;
import net.samagames.persistanceapi.beans.statistics.PlayerStatisticsBean;

import javax.lang.model.element.Modifier;
import java.beans.ConstructorProperties;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
public class Generator {
    private static final List<JavaFile> toBuild = new ArrayList<>();

    private static final ClassName apimpl = ClassName.get("net.samagames.core", "ApiImplementation");
    private static final ClassName jedis = ClassName.get("redis.clients.jedis", "Jedis");
    private static final ClassName converter = ClassName.get("net.samagames.tools", "TypeConverter");

    public static void main(String[] args) {
        createPlayerStat();

        createCacheLoader();

        build();
    }

    public static void createPlayerStat() {
        List<JavaFile> typeStats = loadGameStats();

        TypeSpec.Builder playerStatsBuilder = TypeSpec.classBuilder("PlayerStats")
                .addModifiers(Modifier.PUBLIC);
        playerStatsBuilder.addSuperinterface(IPlayerStats.class);

        playerStatsBuilder.addField(UUID.class, "playerUUID", Modifier.PRIVATE);
        playerStatsBuilder.addField(apimpl, "api", Modifier.PRIVATE);
        playerStatsBuilder.addField(boolean[].class, "statsToLoad", Modifier.PRIVATE);

        for (JavaFile javaFile : typeStats) {
            playerStatsBuilder.addField(ClassName.get(javaFile.packageName, javaFile.typeSpec.name),
                    javaFile.typeSpec.name.toLowerCase(), Modifier.PRIVATE);
        }
        ClassName playerData = ClassName.get("net.samagames.core.api.player", "PlayerData");
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(apimpl, "api")
                .addParameter(playerData, "player")
                .addParameter(boolean[].class, "statsToLoad")
                .addStatement("this.$N = $N", "api", "api")
                .addStatement("this.$N = $N.getPlayerID()", "playerUUID", "player")
                .addStatement("this.$N = $N", "statsToLoad", "statsToLoad");
        constructor.addStatement("boolean global = statsToLoad[" + GamesNames.GLOBAL.intValue() + "]");

        //Logger.getGlobal().info("GamesNames: " + Arrays.toString(GamesNames.values()));

        for (JavaFile javaFile : typeStats) {
            double value = 0;
            GamesNames stat = null;
            for (GamesNames names : GamesNames.values()) {
                double sim = similarity(names.name().toLowerCase(), javaFile.typeSpec.name.toLowerCase());
                if (sim > value) {
                    value = sim;
                    stat = names;
                }
                //Logger.getGlobal().info("value : " + sim + " for: " + names.name() + " lel " + javaFile.typeSpec.name.toLowerCase());
            }

            constructor.addStatement("if (global || statsToLoad[" + Objects.requireNonNull(stat).intValue() + "]) \n"
                    + "this." + javaFile.typeSpec.name.toLowerCase() + " = new " + javaFile.typeSpec.name + "(player)");
        }
        playerStatsBuilder.addMethod(constructor.build());

        MethodSpec getapi = MethodSpec.methodBuilder("getApi")
                .addModifiers(Modifier.PUBLIC)
                .returns(apimpl)
                .addStatement("return $N", "api")
                .build();
        playerStatsBuilder.addMethod(getapi);

        MethodSpec getplayer = MethodSpec.methodBuilder("getPlayerUUID")
                .addModifiers(Modifier.PUBLIC)
                .returns(UUID.class)
                .addStatement("return $N", "playerUUID")
                .build();
        playerStatsBuilder.addMethod(getplayer);

        MethodSpec.Builder upStat = MethodSpec.methodBuilder("updateStats")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(void.class);
        for (JavaFile javaFile : typeStats) {
            String variable = javaFile.typeSpec.name.toLowerCase();
            String code = "if (" + variable + " != null)\n" +
                    "   " + variable + ".update()";
            upStat.addStatement(code);

        }
        playerStatsBuilder.addMethod(upStat.build());


        MethodSpec.Builder refreshStat = MethodSpec.methodBuilder("refreshStats")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(boolean.class);

        for (JavaFile javaFile : typeStats) {
            String variable = javaFile.typeSpec.name.toLowerCase();
            String code = "if (" + variable + " != null)\n" +
                    "   " + variable + ".refresh()";
            refreshStat.addStatement(code);

        }
        refreshStat.addStatement("return true");
        playerStatsBuilder.addMethod(refreshStat.build());

        for (JavaFile javaFile : typeStats) {
            ClassName className = ClassName.get(javaFile.packageName, javaFile.typeSpec.name);
            String variable = javaFile.typeSpec.name.toLowerCase();
            MethodSpec.Builder getter = MethodSpec.methodBuilder("get" + javaFile.typeSpec.name)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(className);

            getter.addStatement("return $N", variable);
            playerStatsBuilder.addMethod(getter.build());

            MethodSpec.Builder setter = MethodSpec.methodBuilder("set" + javaFile.typeSpec.name)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void.class)
                    .addParameter(className, variable);

            setter.addStatement("this.$N = $N", variable, variable);
            playerStatsBuilder.addMethod(setter.build());

        }
        toBuild.add(JavaFile.builder("net.samagames.core.api.stats", playerStatsBuilder.build()).build());

        //SETTINGS
        String settingPackage = "net.samagames.core.api.settings";
        String settingPackageI = "net.samagames.api.settings";
        TypeSpec implClass = createImplementationClass(settingPackageI, PlayerSettingsBean.class, "settings:", false);
        toBuild.add(JavaFile.builder(settingPackage, Objects.requireNonNull(implClass)).build());
        //END SETTINGS

        TypeSpec shop = createSImplementationClass("net.samagames.api.shops", ItemDescriptionBean.class);
        toBuild.add(JavaFile.builder("net.samagames.core.api.shops", Objects.requireNonNull(shop)).build());

        TypeSpec transaction = createSImplementationClass("net.samagames.api.shops", TransactionBean.class);
        toBuild.add(JavaFile.builder("net.samagames.core.api.shops", Objects.requireNonNull(transaction)).build());

    }

    public static List<JavaFile> loadGameStats() {
        List<JavaFile> stats = new ArrayList<>();
        String package_ = "net.samagames.core.api.stats.games";
        String packageI_ = "net.samagames.api.stats.games";
        Field[] playerStatisticFields = PlayerStatisticsBean.class.getDeclaredFields();
        for (Field field : playerStatisticFields) {
            field.setAccessible(true);
            TypeSpec implClass = createImplementationClass(packageI_, field.getType(), "statistic:", true);

            JavaFile file = JavaFile.builder(package_, Objects.requireNonNull(implClass)).build();
            stats.add(file);
            toBuild.add(file);
        }
        return stats;
    }

    @SuppressWarnings("rawtypes")
    public static TypeSpec createImplementationClass(String package_, Class type, String serializeKey) {
        return createImplementationClass(package_, type, serializeKey, false);
    }

    @SuppressWarnings({"rawtypes", "MismatchedQueryAndUpdateOfCollection"})
    public static TypeSpec createImplementationClass(String package_, Class type, String serializeKey, boolean isUpdatable) {
        String name = type.getSimpleName().replaceAll("Bean", "");
        Method[] subDeclaredMethods = type.getDeclaredMethods();

        TypeSpec.Builder object = TypeSpec.classBuilder(name)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(package_, "I" + name))
                .superclass(type);
        ClassName pdata = ClassName.get("net.samagames.core.api.player", "PlayerData");
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(pdata, "playerData")
                .addParameter(type, "bean");

        StringBuilder sup = new StringBuilder("super(playerData.getPlayerID()\n");

        //CONSTRUCTOR START
        Constructor constructor1 = type.getConstructors()[0];
        if (!constructor1.isAnnotationPresent(ConstructorProperties.class))
            return null;
        ConstructorProperties annotation = (ConstructorProperties) constructor1.getAnnotation(ConstructorProperties.class);

        //Not efficiency but do the job and don't care for compilation
        int i = 0;
        for (String parameterName : annotation.value()) {
            //Don't do the first iteration
            if (i == 0) {
                i++;
                continue;
            }
            double similitudeMax = 0.0;
            String methodName = "";
            for (Method method : subDeclaredMethods) {
                if (method.getName().startsWith("get")
                        || method.getName().startsWith("is")) {
                    double similitude = similarity(parameterName.toLowerCase(), method.getName().toLowerCase());
                    if (similitude > similitudeMax) {
                        similitudeMax = similitude;
                        methodName = method.getName();
                    }
                }
            }
            sup.append(",bean.").append(methodName).append("()\n");
        }
        sup.append(")");

        constructor.addStatement(sup.toString());
        constructor.addStatement("this.api = ($T) $T.get()", apimpl, apimpl);
        constructor.addStatement("this.$N = $N", "playerData", "playerData");

        object.addMethod(constructor.build());
        //CONSTRUCTOR END

        //Constructor 2 START
        MethodSpec.Builder constructor2 = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(pdata, "playerData");

        StringBuilder instruction = new StringBuilder("super(playerData.getPlayerID()\n");

        boolean lol = true;
        for (Parameter parameter : type.getConstructors()[0].getParameters()) {
            if (lol) {
                lol = false;
                continue;
            }

            if (parameter.getType().equals(int.class)) {
                instruction.append(",0\n");
            } else if (parameter.getType().equals(long.class)) {
                instruction.append(",0\n");
            } else if (parameter.getType().equals(double.class)) {
                instruction.append(",0.0\n");
            } else if (parameter.getType().equals(boolean.class)) {
                instruction.append(",false\n");
            } else {
                instruction.append(", null\n");
            }
        }
        instruction.append(")");

        constructor2.addStatement(instruction.toString());
        constructor2.addStatement("this.api = ($T) $T.get()", apimpl, apimpl);
        constructor2.addStatement("this.$N = $N", "playerData", "playerData");

        object.addMethod(constructor2.build());
        //CONSTRUCTOR 2 END

        object.addField(pdata, "playerData", Modifier.PROTECTED);
        object.addField(apimpl, "api", Modifier.PROTECTED);

        List<String> createdFields = new ArrayList<>();

        for (Method method : subDeclaredMethods) {
            String methodName = method.getName();
            if (method.getParameters().length > 0) {
                boolean isIncrementable = false;
                Class<?> type1 = method.getParameters()[0].getType();
                if (type1.equals(int.class)
                        || type1.equals(long.class)
                        || type1.equals(double.class)
                        || type1.equals(float.class))
                    isIncrementable = true;

                if (methodName.startsWith("set") && isIncrementable) {
                    methodName = "incrBy" + methodName.substring(3);
                    String fieldName = method.getName().substring(3) + "Vector";
                    createdFields.add(fieldName);
                    /*FieldSpec vector = FieldSpec.builder(type1, fieldName)
                            .addModifiers(Modifier.PRIVATE).initializer("0").build();
                    object.addField(vector);*/

                    MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName);
                    if (method.getParameterCount() > 0) {
                        for (Parameter parameter : method.getParameters()) {
                            builder.addParameter(parameter.getType(), parameter.getName());
                        }
                    }
                    builder.addModifiers(Modifier.PUBLIC);
                    builder.returns(method.getReturnType());
                    builder.addStatement(method.getName() + "(get" + method.getName().substring(3) + "() + arg0)");
                    object.addMethod(builder.build());
                }
            }
        }

        //UPDATE START
        MethodSpec.Builder update = MethodSpec.methodBuilder("update")
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addAnnotation(Override.class);

        if (isUpdatable) {
            update.addStatement("    try {\n" +
                    "  this.api.getGameServiceManager().update" + type.getSimpleName().replace("StatisticsBean", "") + "Statistics(playerData.getPlayerBean(), this);\n" +
                    "} catch (Exception e) {\n" +
                    "  e.printStackTrace();\n" +
                    "}");
        }

        /*update.addStatement("$T jedis = this.api.getBungeeResource()", jedis);

        Method[] declaredMethods = type.getDeclaredMethods();
        for (Method getters : declaredMethods)
        {
            int test = 0;
            if (getters.getName().startsWith("get"))
            {
                test = 3;
            }else if (getters.getName().startsWith("is"))
            {
                test = 2;
            }
            if (test != 0)
            {
                String command = "hset";
                String dataName = "\"\" + " + getters.getName() + "()";
                if (createdFields.contains(getters.getName().substring(3) + "Vector"))
                {
                    command = "hincrBy";
                    if (getters.getReturnType().equals(float.class)
                            || getters.getReturnType().equals(double.class))
                        command = "hincrByFloat";
                    dataName = getters.getName().substring(3) + "Vector";
                }

                update.addStatement("jedis." + command + "(\"" + serializeKey + "\" + playerData.getPlayerID() + \":"
                        + type.getSimpleName() +"\", \"" + getters.getName().substring(test) + "\", "
                        + dataName + ")");
            }
        }

        update.addStatement("jedis.close()");*/
        object.addMethod(update.build());
        //UPDATE END

        //REFRESH START
        MethodSpec.Builder refresh = MethodSpec.methodBuilder("refresh")
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addAnnotation(Override.class);
        if (isUpdatable) {
            refresh.addStatement("    try {\n" +
                    "  copy(this.api.getGameServiceManager().get" + type.getSimpleName().replace("StatisticsBean", "") + "Statistics(playerData.getPlayerBean()));\n" +
                    "} catch (Exception e) {\n" +
                    "  e.printStackTrace();\n" +
                    "}");
        }

        /*refresh.addStatement("$T jedis = this.api.getBungeeResource()", jedis);

        for (Method getters : type.getDeclaredMethods())
        {
            if (getters.getName().startsWith("set"))
            {
                refresh.addStatement(getters.getName() + "($T.convert(" + getters.getParameters()[0].getType().getName()
                        + ".class, jedis.hget(\"" + serializeKey + "\" + playerData.getPlayerID() + \":"
                        + type.getSimpleName() +"\", \"" + getters.getName().substring(3) + "\")))", converter);
            }
        }

        refresh.addStatement("jedis.close()");*/
        object.addMethod(refresh.build());
        //REFRESH END

        //copy START
        MethodSpec.Builder copy = MethodSpec.methodBuilder("copy")
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addParameter(type, "data");

        for (Method setters : type.getDeclaredMethods()) {
            if (setters.getName().startsWith("set")) {
                copy.addStatement(setters.getName() + "(data." + (setters.getParameters()[0].getType().equals(boolean.class) ? "is" : "get") + setters.getName().substring(3) + "())");
            }
        }
        object.addMethod(copy.build());

        return object.build();
    }

    @SuppressWarnings("rawtypes")
    public static TypeSpec createSImplementationClass(String package_, Class type) {
        String name = type.getSimpleName().replaceAll("Bean", "");
        Method[] subDeclaredMethods = type.getDeclaredMethods();

        TypeSpec.Builder object = TypeSpec.classBuilder(name)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ClassName.get(package_, "I" + name))
                .superclass(type);
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(type, "bean");

        StringBuilder sup = new StringBuilder("super(\n");

        //CONSTRUCTOR START
        Constructor constructor1 = type.getConstructors()[0];
        if (!constructor1.isAnnotationPresent(ConstructorProperties.class))
            return null;
        ConstructorProperties annotation = (ConstructorProperties) constructor1.getAnnotation(ConstructorProperties.class);

        //Not efficiency but do the job and don't care for compilation
        for (String parameterName : annotation.value()) {
            double similitudeMax = 0.0;
            String methodName = "";
            for (Method method : subDeclaredMethods) {
                if (method.getName().startsWith("get")
                        || method.getName().startsWith("is")) {
                    double similitude = similarity(parameterName.toLowerCase(), method.getName().toLowerCase());
                    if (similitude > similitudeMax) {
                        similitudeMax = similitude;
                        methodName = method.getName();
                    }
                }
            }
            sup.append("bean.").append(methodName).append("()\n,");
        }
        sup = new StringBuilder(sup.substring(0, sup.length() - 1));
        sup.append(")");

        constructor.addStatement(sup.toString());
        object.addMethod(constructor.build());
        //CONSTRUCTOR END

        //Constructor 2 START
        MethodSpec.Builder constructor2 = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC);

        StringBuilder instruction = new StringBuilder("super(\n");

        for (Parameter parameter : type.getConstructors()[0].getParameters()) {

            if (parameter.getType().equals(int.class)) {
                instruction.append("0\n,");
            } else if (parameter.getType().equals(long.class)) {
                instruction.append("0\n,");
            } else if (parameter.getType().equals(double.class)) {
                instruction.append("0.0\n,");
            } else if (parameter.getType().equals(boolean.class)) {
                instruction.append("false\n,");
            } else {
                instruction.append("null\n,");
            }
        }
        instruction = new StringBuilder(instruction.substring(0, instruction.length() - 1));
        instruction.append(")");

        constructor2.addStatement(instruction.toString());
        object.addMethod(constructor2.build());
        //CONSTRUCTOR 2 END


        return object.build();
    }

    @SuppressWarnings("rawtypes")
    public static void createCacheLoader() {
        List<Class> toCreate = new ArrayList<>();
        toCreate.add(GroupsBean.class);
        toCreate.add(PlayerBean.class);

        //Imports
        ClassName jedis = ClassName.get("redis.clients.jedis", "Jedis");
        ClassName converter = ClassName.get("net.samagames.tools", "TypeConverter");

        //Generation
        TypeSpec.Builder object = TypeSpec.classBuilder("CacheLoader")
                .addModifiers(Modifier.PUBLIC);

        //Loaders
        for (Class classe : toCreate) {
            MethodSpec.Builder getter = MethodSpec.methodBuilder("load")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(jedis, "jedis")
                    .addParameter(String.class, "key")
                    .addParameter(classe, "objet")
                    .returns(void.class);

            for (Method method : classe.getDeclaredMethods()) {
                if (method.getName().startsWith("set")) {
                    getter.addStatement("objet." + method.getName() + "($T.convert("
                            + method.getParameters()[0].getType().getName() + ".class, jedis.hget(key, \""
                            + method.getName().substring(3) + "\")))", converter);
                }
            }
            object.addMethod(getter.build());
        }

        //Setters
        for (Class classe : toCreate) {
            MethodSpec.Builder setter = MethodSpec.methodBuilder("send")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .addParameter(jedis, "jedis")
                    .addParameter(String.class, "key")
                    .addParameter(classe, "objet")
                    .returns(void.class);

            for (Method method : classe.getDeclaredMethods()) {
                if (method.getName().startsWith("get")) {
                    setter.addStatement("jedis.hset(key, \"" + method.getName().substring(3) + "\","
                            + " \"\" + objet." + method.getName() + "())");
                }
            }
            object.addMethod(setter.build());
        }

        JavaFile file = JavaFile.builder("net.samagames.core.utils", object.build()).build();
        toBuild.add(file);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void build() {
        try {
            File file = new File("./Generation");
            file.delete();
            for (JavaFile javaFile : toBuild) {
                javaFile.writeTo(file);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static MethodSpec getMethod(String name, TypeName retur) {
        MethodSpec.Builder getter = MethodSpec.methodBuilder(name);
        getter.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
        getter.returns(retur);
        return getter.build();
    }

    public static MethodSpec getMethod(String name, Type retur) {
        return getMethod(name, TypeName.get(retur));
    }

    /**
     * Calculates the similarity (a number within 0 and 1) between two strings.
     */
    private static double similarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { // longer should always have greater length
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0; /* both strings are zero length */
        }
        /* // If you have StringUtils, you can use it to calculate the edit distance:
        return (longerLength - StringUtils.getLevenshteinDistance(longer, shorter)) /
                                                             (double) longerLength; */
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;

    }

    // Example implementation of the Levenshtein Edit Distance
    // See http://r...content-available-to-author-only...e.org/wiki/Levenshtein_distance#Java
    private static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue),
                                    costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
