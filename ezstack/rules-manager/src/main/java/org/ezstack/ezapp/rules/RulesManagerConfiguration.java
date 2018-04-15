package org.ezstack.ezapp.rules;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;

public class RulesManagerConfiguration {

    @NotEmpty
    @JsonProperty("zookeeperHosts")
    private String _zookeeperHosts;

    @NotNull
    @JsonProperty("partitionCount")
    private Integer _partitionCount;

    @NotNull
    @JsonProperty("replicationFactor")
    private Integer _replicationFactor;

    @NotEmpty
    @JsonProperty("bootstrapTopicName")
    private String _bootstrapTopicName;

    @NotEmpty
    @JsonProperty("shutdownTopicName")
    private String _shutdownTopicName;

    @NotEmpty
    @JsonProperty("kafkaBootstrapServers")
    private String _kafkaBootstrapServers;

    @NotNull
    @JsonProperty("bootstrapperContainerCount")
    private Integer _bootstrapperContainerCount;

    @NotEmpty
    @JsonProperty("bootstrapperPackagePath")
    private String _bootstrapperPackagePath;

    @NotEmpty
    @JsonProperty("samzaAppRunnerPath")
    private String _samzaAppRunnerPath;

    public String getZookeeperHosts() {
        return _zookeeperHosts;
    }

    public int getPartitionCount() {
        return _partitionCount;
    }

    public int getReplicationFactor() {
        return _replicationFactor;
    }

    public String getBootstrapTopicName() {
        return _bootstrapTopicName;
    }

    public String getShutdownTopicName() {
        return _shutdownTopicName;
    }

    public String getKafkaBootstrapServers() {
        return _kafkaBootstrapServers;
    }

    public int getBootstrapperContainerCount() {
        return _bootstrapperContainerCount;
    }

    public String getBootstrapperPackagePath() {
        return _bootstrapperPackagePath;
    }

    public String getSamzaAppRunnerPath() {
        return _samzaAppRunnerPath;
    }
}