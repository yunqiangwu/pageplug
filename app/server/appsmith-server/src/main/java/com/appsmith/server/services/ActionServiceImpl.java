package com.appsmith.server.services;

import com.appsmith.external.models.ActionConfiguration;
import com.appsmith.external.models.ActionExecutionResult;
import com.appsmith.external.models.DatasourceConfiguration;
import com.appsmith.external.models.Param;
import com.appsmith.external.plugins.PluginExecutor;
import com.appsmith.server.constants.AnalyticsEvents;
import com.appsmith.server.constants.FieldName;
import com.appsmith.server.domains.Action;
import com.appsmith.server.domains.Datasource;
import com.appsmith.server.domains.Page;
import com.appsmith.server.domains.PageAction;
import com.appsmith.server.domains.Plugin;
import com.appsmith.server.dtos.ExecuteActionDTO;
import com.appsmith.server.exceptions.AppsmithError;
import com.appsmith.server.exceptions.AppsmithException;
import com.appsmith.server.helpers.PluginExecutorHelper;
import com.appsmith.server.repositories.ActionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import javax.lang.model.SourceVersion;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.appsmith.server.helpers.BeanCopyUtils.copyNewFieldValuesIntoOldObject;
import static com.appsmith.server.helpers.MustacheHelper.extractMustacheKeys;

@Slf4j
@Service
public class ActionServiceImpl extends BaseService<ActionRepository, Action, String> implements ActionService {

    private final ActionRepository repository;
    private final DatasourceService datasourceService;
    private final PluginService pluginService;
    private final PageService pageService;
    private final ObjectMapper objectMapper;
    private final DatasourceContextService datasourceContextService;
    private final PluginExecutorHelper pluginExecutorHelper;

    @Autowired
    public ActionServiceImpl(Scheduler scheduler,
                             Validator validator,
                             MongoConverter mongoConverter,
                             ReactiveMongoTemplate reactiveMongoTemplate,
                             ActionRepository repository,
                             DatasourceService datasourceService,
                             PluginService pluginService,
                             PageService pageService,
                             AnalyticsService analyticsService,
                             ObjectMapper objectMapper,
                             DatasourceContextService datasourceContextService,
                             PluginExecutorHelper pluginExecutorHelper) {
        super(scheduler, validator, mongoConverter, reactiveMongoTemplate, repository, analyticsService);
        this.repository = repository;
        this.datasourceService = datasourceService;
        this.pluginService = pluginService;
        this.pageService = pageService;
        this.objectMapper = objectMapper;
        this.datasourceContextService = datasourceContextService;
        this.pluginExecutorHelper = pluginExecutorHelper;
    }

    /**
     * This function updates an existing action in the DB. We are completely overriding the base update function to
     * ensure that we can populate the JsonPathKeys field in the ActionConfiguration based on any changes that may
     * have happened in the action object.
     * <p>
     * Calling the base function would make redundant DB calls and slow down this API unnecessarily.
     *
     * @param id
     * @param action
     * @return
     */
    @Override
    public Mono<Action> update(String id, Action action) {
        Set<String> invalids = new HashSet<>();
        if (id == null) {
            return Mono.error(new AppsmithException(AppsmithError.INVALID_PARAMETER, FieldName.ID));
        }

        Mono<Action> replaceOrCreateNewDataSourceMono = replaceOrCreateNewDataSource(action);
        Mono<Action> dbActionMono = repository.findById(id)
                .switchIfEmpty(Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "action", id)));

        return Mono.zip(replaceOrCreateNewDataSourceMono, dbActionMono)
                .map(tuple -> {
                    Action updatedActionWithDatasource = tuple.getT1();
                    Action dbAction = tuple.getT2();
                    copyNewFieldValuesIntoOldObject(updatedActionWithDatasource, dbAction);
                    return dbAction;
                })
                .flatMap(this::validateAndSaveActionToRepository)
                .map(act -> {
                            analyticsService
                                    .sendEvent(AnalyticsEvents.UPDATE + "_" + act.getClass().getSimpleName().toUpperCase(),
                                            act);
                            return act;
                        }
                );
    }

    private Mono<Action> replaceOrCreateNewDataSource(Action action) {
        Datasource datasource = action.getDatasource();
        if (datasource != null) {
            //Update action contains a change for datasource
            if (datasource.getId() != null) {
                //Datasource changed to another existing data source. Confirm and return.
                return datasourceService.findById(datasource.getId())
                        .switchIfEmpty(Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "datasource", datasource.getId())))
                        .map(datasource1 -> {
                            action.setDatasource(datasource1);
                            action.setDatasourceId(datasource1.getId());
                            return action;
                        });
            }

            //New datasource needs to be created here.
            return datasourceService.create(datasource)
                    .map(datasource1 -> {
                        action.setDatasource(datasource1);
                        action.setDatasourceId(datasource1.getId());
                        return action;
                    });
        }
        //No changes in the datasource.
        return Mono.just(action);
    }

    private Boolean validateActionName(String name) {
        boolean isValidName = SourceVersion.isName(name);
        String pattern = "^((?=[A-Za-z0-9_])(?![\\\\-]).)*$";
        boolean doesPatternMatch = name.matches(pattern);
        return (isValidName && doesPatternMatch);
    }

    @Override
    public Mono<Action> create(@NotNull Action action) {
        if (action.getId() != null) {
            return Mono.error(new AppsmithException(AppsmithError.INVALID_PARAMETER, "id"));
        }
        return validateAndSaveActionToRepository(action);
    }

    private Mono<Action> validateAndSaveActionToRepository(Action action) {
        //Default the validity to true and invalids to be an empty set.
        Set<String> invalids = new HashSet<>();
        action.setIsValid(true);

        if (action.getName() == null || action.getName().trim().isEmpty()) {
            return Mono.error(new AppsmithException(AppsmithError.INVALID_PARAMETER, FieldName.NAME));
        }

        if (!validateActionName(action.getName())) {
            action.setIsValid(false);
            invalids.add(AppsmithError.INVALID_ACTION_NAME.getMessage());
        }

        if (action.getActionConfiguration() == null) {
            action.setIsValid(false);
            invalids.add(AppsmithError.NO_CONFIGURATION_FOUND_IN_ACTION.getMessage());
        }

        if (action.getDatasource() == null) {
            action.setIsValid(false);
            invalids.add(AppsmithError.DATASOURCE_NOT_GIVEN.getMessage());
            action.setInvalids(invalids);
            return super.create(action);
        }

        Mono<Datasource> datasourceMono;
        if (action.getDatasource().getId() == null) {
            //No data source exists. The action is also trying to create the data source.
            datasourceMono = datasourceService.create(action.getDatasource());
        } else {
            //Data source already exists. Find the same.
            datasourceMono = datasourceService.findById(action.getDatasource().getId())
                    .switchIfEmpty(Mono.defer(() -> {
                        action.setIsValid(false);
                        invalids.add(AppsmithError.NO_RESOURCE_FOUND.getMessage(FieldName.DATASOURCE, action.getDatasourceId()));
                        return Mono.just(action.getDatasource());
                    }));
        }

        Mono<Plugin> pluginMono = datasourceMono.flatMap(datasource -> pluginService.findById(datasource.getPluginId())
                .switchIfEmpty(Mono.defer(() -> {
                    action.setIsValid(false);
                    invalids.add(AppsmithError.NO_RESOURCE_FOUND.getMessage(FieldName.PLUGIN, datasource.getPluginId()));
                    return Mono.just(new Plugin());
                })));

        return pluginMono
                .zipWith(datasourceMono)
                //Set plugin in the action before saving.
                .map(tuple -> {
                    Plugin plugin = tuple.getT1();
                    if (plugin.getId() == null) {
                        log.debug("Plugin not found in the datasource.");
                    }
                    Datasource datasource = tuple.getT2();
                    action.setDatasource(datasource);
                    action.setDatasourceId(datasource.getId());
                    action.setInvalids(invalids);
                    return action;
                }).map(act -> extractAndSetJsonPathKeys(act))
                .flatMap(super::create)
                .switchIfEmpty(Mono.error(new AppsmithException(AppsmithError.REPOSITORY_SAVE_FAILED)));
    }

    /**
     * This function extracts all the mustache template keys (as per the regex) and returns them to the calling fxn
     * This set of keys is stored separately in the field `jsonPathKeys` in the action object. The client
     * uses the set `jsonPathKeys` to simplify it's value substitution.
     *
     * @param action
     * @return
     */
    private Set<String> extractKeysFromAction(Action action) {
        if (action.getActionConfiguration() == null) {
            return new HashSet<>();
        }

        // Convert the object to String as a preparation to send it to mustache extraction
        try {
            String actionConfigStr = objectMapper.writeValueAsString(action.getActionConfiguration());
            return extractMustacheKeys(actionConfigStr);
        } catch (JsonProcessingException e) {
            log.error("Exception caught while extracting mustache keys from action configuration. ", e);
        }
        return new HashSet<>();
    }

    /**
     * This function extracts the mustache keys and sets them in the field jsonPathKeys in the action object
     *
     * @param action
     * @return
     */
    private Action extractAndSetJsonPathKeys(Action action) {
        Set<String> actionKeys = extractKeysFromAction(action);
        Set<String> datasourceKeys = datasourceService.extractKeysFromDatasource(action.getDatasource());
        Set<String> keys = new HashSet<String>() {{
            addAll(actionKeys);
            addAll(datasourceKeys);
        }};
        action.setJsonPathKeys(keys);
        return action;
    }

    public Mono<Page> bindPageToAction(Action action, String pageId) {
        Mono<Page> pageMono = pageService.findById(pageId);
        action.setPageId(pageId);
        return pageMono
                //If page exists, then continue forward
                .then(repository.save(action))
                .flatMap(action1 -> pageMono
                        .map(page -> {
                            PageAction pageAction = new PageAction();
                            pageAction.setId(action1.getId());

                            List<PageAction> actions = page.getActions();

                            if (actions == null) {
                                actions = new ArrayList<>();
                            }

                            actions.add(pageAction);
                            page.setActions(actions);
                            return page;
                        })
                        .flatMap(pageService::save)
                );
    }

    @Override
    public Mono<ActionExecutionResult> executeAction(ExecuteActionDTO executeActionDTO) {
        Action actionFromDto = executeActionDTO.getAction();

        // 1. Validate input parameters which are required for mustache replacements
        List<Param> params = executeActionDTO.getParams();
        if (params != null && !params.isEmpty()) {
            for (Param param : params) {
                if (param.getValue() == null) {
                    return Mono.error(new AppsmithException(AppsmithError.ACTION_RUN_KEY_VALUE_INVALID, param.getKey(), param.getValue()));
                }
            }
        }

        // 2. Fetch the query from the DB/from dto to get the type
        Mono<Action> actionMono;
        if (actionFromDto.getId() != null) {
            actionMono = repository.findById(actionFromDto.getId())
                    .switchIfEmpty(Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "action", actionFromDto.getId())))
                    .flatMap(action -> {
                        // This is separately done instead of fetching from the repository using id and isValid. This is
                        // because we want to error out with two different statuses -> Wrong action id OR Invalid action
                        if (action.getIsValid() == false) {
                            return Mono.error(new AppsmithException(AppsmithError.INVALID_ACTION));
                        }
                        return Mono.just(action);
                    });
        } else {
            actionMono = Mono.just(actionFromDto);
        }

        // 3. Instantiate the implementation class based on the query type

        Mono<Datasource> datasourceMono = actionMono
                .flatMap(action -> {
                    if (action.getDatasourceId() != null) {
                        return datasourceService.findById(action.getDatasourceId());
                    } else if (action.getDatasource() != null && action.getDatasource().getId() != null) {
                        return datasourceService.findById(action.getDatasource().getId());
                    }
                    //The data source in the action has not been persisted.
                    if (action.getDatasource() != null) {
                        return Mono.just(action.getDatasource());
                    } else {
                        return Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "Valid action"));
                    }
                })
                .switchIfEmpty(Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "resource")));

        Mono<Plugin> pluginMono = datasourceMono
                .flatMap(datasource -> {
                    if (datasource.getIsValid() != null && datasource.getIsValid() == false) {
                        return Mono.error(new AppsmithException(AppsmithError.INVALID_DATASOURCE));
                    }
                    return pluginService.findById(datasource.getPluginId());
                })
                .switchIfEmpty(Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "plugin")));

        Mono<PluginExecutor> pluginExecutorMono = pluginExecutorHelper.getPluginExecutor(pluginMono);

        // 4. Execute the query
        return actionMono
                .flatMap(action -> datasourceMono.zipWith(pluginExecutorMono, (datasource, pluginExecutor) -> {
                    DatasourceConfiguration datasourceConfiguration;
                    ActionConfiguration actionConfiguration;
                    //Do variable substitution before invoking the plugin
                    //Do this only if params have been provided in the execute command
                    if (executeActionDTO.getParams() != null && !executeActionDTO.getParams().isEmpty()) {
                        Map<String, String> replaceParamsMap = executeActionDTO
                                .getParams()
                                .stream()
                                .collect(Collectors.toMap(Param::getKey, Param::getValue,
                                        // In case of a conflict, we pick the older value
                                        (oldValue, newValue) -> oldValue)
                                );
                        datasourceConfiguration = (DatasourceConfiguration) variableSubstitution(datasource.getDatasourceConfiguration(), replaceParamsMap);
                        actionConfiguration = (ActionConfiguration) variableSubstitution(action.getActionConfiguration(), replaceParamsMap);
                    } else {
                        datasourceConfiguration = datasource.getDatasourceConfiguration();
                        actionConfiguration = action.getActionConfiguration();
                    }
                    Integer timeoutDuration = actionConfiguration.getTimeoutInMillisecond();
                    log.debug("Got the timeoutDuration to be: {} ms for action: {}", timeoutDuration, action.getName());
                    return datasourceContextService
                            .getDatasourceContext(datasource)
                            //Now that we have the context (connection details, execute the action
                            .flatMap(resourceContext -> pluginExecutor.execute(
                                    resourceContext.getConnection(),
                                    datasourceConfiguration,
                                    actionConfiguration))
                            .timeout(Duration.ofMillis(timeoutDuration));
                }))
                .flatMap(obj -> obj);
    }

    @Override
    public Mono<Action> save(Action action) {
        return repository.save(action);
    }

    @Override
    public Mono<Action> findByName(String name) {
        return repository.findByName(name);
    }


    /**
     * This function replaces the variables in the Object with the actual params
     */
    public Object variableSubstitution(Object configuration,
                                       Map<String, String> replaceParamsMap) {

        try {
            // Convert the object to String as a preparation to send it to mustacheReplacement
            String objectInJsonString = objectMapper.writeValueAsString(configuration);
            objectInJsonString = mustacheReplacement(objectInJsonString, configuration.getClass().getSimpleName(), replaceParamsMap);
            return objectMapper.readValue(objectInJsonString, configuration.getClass());
        } catch (Exception e) {
            log.error("Exception caught while substituting values in mustache template.", e);
        }
        return configuration;
    }

    /**
     * @param template    : This is the string which contains {{key}} which would be replaced with value
     * @param name        : This is the class name of the object from which template string was created
     * @param keyValueMap : This is the map of keys with values.
     * @return It finally returns the string in which all the keys in template have been replaced with values.
     */
    private String mustacheReplacement(String template, String name, Map<String, String> keyValueMap) {
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(new StringReader(template), name);
        Writer writer = new StringWriter();
        mustache.execute(writer, keyValueMap);

        return writer.toString();
    }

    @Override
    public Mono<Action> delete(String id) {
        Mono<Action> actionMono = repository.findById(id)
                .switchIfEmpty(Mono.error(new AppsmithException(AppsmithError.NO_RESOURCE_FOUND, "action", id)));
        return actionMono
                .map(action -> {
                    if (action.getPageId() == null) {
                        //No page id implies that the action is not bound to a page yet. Safe to delete it
                        return action;
                    }
                    Mono<Page> pageMono = pageService.findById(action.getPageId());
                    return pageMono
                            .map(page -> {
                                List<PageAction> actions = page.getActions();
                                actions = actions.stream().filter(a -> a.getId() != action.getId()).collect(Collectors.toList());
                                page.setActions(actions);
                                return page;
                            })
                            .flatMap(pageService::save)
                            .thenReturn(action);
                })
                .flatMap(toDelete ->
                        repository.delete((Action) toDelete)
                                .thenReturn(toDelete))
                .map(deletedObj -> {
                    analyticsService.sendEvent(AnalyticsEvents.DELETE + "_" + deletedObj.getClass().getSimpleName().toUpperCase(), (Action) deletedObj);
                    return (Action) deletedObj;
                });
    }

    @Override
    public Flux<Action> get(MultiValueMap<String, String> params) {
        Action actionExample = new Action();
        if (params.getFirst(FieldName.NAME) != null) {
            actionExample.setName(params.getFirst(FieldName.NAME));
        }
        Sort sort = new Sort(Direction.ASC, FieldName.NAME );
        return repository.findAll(Example.of(actionExample), sort);
    }
}
