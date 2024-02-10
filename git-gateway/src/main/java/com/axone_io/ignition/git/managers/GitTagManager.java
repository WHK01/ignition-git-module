package com.axone_io.ignition.git.managers;

import com.inductiveautomation.ignition.common.JsonUtilities;
import com.inductiveautomation.ignition.common.browsing.BrowseFilter;
import com.inductiveautomation.ignition.common.browsing.Results;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.tags.TagUtilities;
import com.inductiveautomation.ignition.common.tags.browsing.NodeAttribute;
import com.inductiveautomation.ignition.common.tags.browsing.NodeDescription;
import com.inductiveautomation.ignition.common.tags.config.CollisionPolicy;
import com.inductiveautomation.ignition.common.tags.config.TagConfigurationModel;
import com.inductiveautomation.ignition.common.tags.config.properties.WellKnownTagProps;
import com.inductiveautomation.ignition.common.tags.model.TagConstants;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.BasicTagPath;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.axone_io.ignition.git.GatewayHook.context;
import static com.axone_io.ignition.git.managers.GitManager.clearDirectory;
import static com.axone_io.ignition.git.managers.GitManager.getProjectFolderPath;
import static com.inductiveautomation.ignition.common.tags.TagUtilities.TAG_GSON;

public class GitTagManager {
    private final static LoggerEx logger = LoggerEx.newBuilder().build(GitTagManager.class);

    public static void importTagManager(String projectName) {
        // handle udts first
        Path projectDir = getProjectFolderPath(projectName);
        File udtsProjectDir = projectDir.resolve("tags").toFile();

        GatewayTagManager gatewayTagManager = context.getTagManager();
        if (udtsProjectDir.exists()) {
            File[] files = udtsProjectDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String udtProviderName = FilenameUtils.removeExtension(file.getName());
                    TagProvider udtProvider = gatewayTagManager.getTagProvider(udtProviderName);
                    if (udtProvider != null) {
                        try {
                            udtProvider.importTagsAsync(new BasicTagPath(""), FileUtils.readFileToString(file, StandardCharsets.UTF_8.toString()), "JSON", CollisionPolicy.Overwrite, null);
                        } catch (IOException e) {
                            logger.warn("An error occurred while importing '" + udtProviderName + "' tags.", e);
                        }
                    }
                }
            }
        }

//        Path projectDir = getProjectFolderPath(projectName);
        File tagsProjectDir = projectDir.resolve("tags").toFile();

//        GatewayTagManager gatewayTagManager = context.getTagManager();
        if (tagsProjectDir.exists()) {
            File[] files = tagsProjectDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String providerName = FilenameUtils.removeExtension(file.getName());
                    TagProvider tagProvider = gatewayTagManager.getTagProvider(providerName);
                    if (tagProvider != null) {
                        try {
                            tagProvider.importTagsAsync(new BasicTagPath(""), FileUtils.readFileToString(file, StandardCharsets.UTF_8.toString()), "JSON", CollisionPolicy.Overwrite, null);
                        } catch (IOException e) {
                            logger.warn("An error occurred while importing '" + providerName + "' tags.", e);
                        }
                    }
                }
            }
        }
    }

    public static void exportTag(Path projectFolderPath) {

        try {
            Path tagFolderPath = projectFolderPath.resolve("tags");
            clearDirectory(tagFolderPath);
            Files.createDirectories(tagFolderPath);

            for (TagProvider tagProvider : context.getTagManager().getTagProviders()) {
                TagPath typesPath = TagPathParser.parse("");
                List<TagPath> tagPaths = new ArrayList<>();
                tagPaths.add(typesPath);

                CompletableFuture<List<TagConfigurationModel>> cfTagModels =
                        tagProvider.getTagConfigsAsync(tagPaths, true, true);
                List<TagConfigurationModel> tModels = cfTagModels.get();

                JsonObject json = TagUtilities.toJsonObject(tModels.get(0));
                JsonElement sortedJson = JsonUtilities.createDeterministicCopy(json);

                Path newFile = tagFolderPath.resolve(tagProvider.getName() + ".json");

                Files.writeString(newFile, TAG_GSON.toJson(sortedJson));
            }

            //export udt

            Path udtFolderPath = projectFolderPath.resolve("udts");
            clearDirectory(udtFolderPath);
            Files.createDirectories(udtFolderPath);
            for (TagProvider tagProvider : context.getTagManager().getTagProviders()) {
                // add and write udts
                BrowseFilter browseFilter = new BrowseFilter();
                browseFilter.setRecursive(true);
                browseFilter.addProperty(WellKnownTagProps.TagType, "UdtType");
                Results<NodeDescription> results = tagProvider.browseAsync(TagConstants.UDT_ROOT_PATH, browseFilter).join();
                List<TagPath> udtPaths = new ArrayList<>();
                for (NodeDescription result : results.getResults()) {
                    // get the udt path
                    Set<NodeAttribute> udtAttrs = result.getAttributes();
                    TagPath udtPath = udtAttrs.

//                    TagPath udtTypesPath = TagPathParser.parse("", "UdtType");
                    udtPaths.add(udtPath);

                }

                CompletableFuture<List<TagConfigurationModel>> cfUdtModels =
                        tagProvider.getTagConfigsAsync(udtPaths, true, true);
                List<TagConfigurationModel> udtModels = cfUdtModels.get();
                JsonObject udtjson = TagUtilities.toJsonObject(udtModels.get(0));
                JsonElement sortedUdtJson = JsonUtilities.createDeterministicCopy(udtjson);

                Path udtFile = udtFolderPath.resolve(tagProvider.getName() + ".json");
                Files.writeString(udtFile, TAG_GSON.toJson(sortedUdtJson));
            }
        } catch (Exception e) {
            logger.error(e.toString(), e);
            throw new RuntimeException(e);
        }
    }
}
