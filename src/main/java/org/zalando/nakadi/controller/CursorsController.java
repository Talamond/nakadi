package org.zalando.nakadi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.nakadi.domain.CursorCommitResult;
import org.zalando.nakadi.domain.ItemsWrapper;
import org.zalando.nakadi.exceptions.InvalidCursorException;
import org.zalando.nakadi.exceptions.NakadiException;
import org.zalando.nakadi.exceptions.NoSuchEventTypeException;
import org.zalando.nakadi.exceptions.NoSuchSubscriptionException;
import org.zalando.nakadi.exceptions.ServiceUnavailableException;
import org.zalando.nakadi.exceptions.Try;
import org.zalando.nakadi.problem.ValidationProblem;
import org.zalando.nakadi.repository.EventTypeRepository;
import org.zalando.nakadi.repository.db.SubscriptionDbRepository;
import org.zalando.nakadi.security.Client;
import org.zalando.nakadi.service.CursorsService;
import org.zalando.nakadi.util.FeatureToggleService;
import org.zalando.nakadi.view.SubscriptionCursor;
import org.zalando.problem.Problem;
import org.zalando.problem.spring.web.advice.Responses;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;
import static org.zalando.nakadi.util.FeatureToggleService.Feature.HIGH_LEVEL_API;
import static org.zalando.problem.MoreStatus.UNPROCESSABLE_ENTITY;
import static org.zalando.problem.spring.web.advice.Responses.create;

@RestController
public class CursorsController {

    private static final Logger LOG = LoggerFactory.getLogger(CursorsController.class);

    private final CursorsService cursorsService;
    private final FeatureToggleService featureToggleService;
    private final SubscriptionDbRepository subscriptionRepository;
    private final EventTypeRepository eventTypeRepository;

    @Autowired
    public CursorsController(final CursorsService cursorsService,
                             final FeatureToggleService featureToggleService,
                             final SubscriptionDbRepository subscriptionRepository,
                             final EventTypeRepository eventTypeRepository) {
        this.cursorsService = cursorsService;
        this.featureToggleService = featureToggleService;
        this.subscriptionRepository = subscriptionRepository;
        this.eventTypeRepository = eventTypeRepository;
    }

    @RequestMapping(path = "/subscriptions/{subscriptionId}/cursors", method = RequestMethod.GET)
    public ResponseEntity<?> getCursors(@PathVariable("subscriptionId") final String subscriptionId,
                                        final NativeWebRequest request,
                                        final Client client) {
        if (!featureToggleService.isFeatureEnabled(HIGH_LEVEL_API)) {
            return new ResponseEntity<>(NOT_IMPLEMENTED);
        }
        try {
            validateSubscriptionReadScopes(client, subscriptionId);
            final List<SubscriptionCursor> cursors = cursorsService.getSubscriptionCursors(subscriptionId);
            return status(OK).body(new ItemsWrapper<>(cursors));
        } catch (final NakadiException e) {
            return create(e.asProblem(), request);
        }
    }

    @RequestMapping(value = "/subscriptions/{subscriptionId}/cursors", method = RequestMethod.POST)
    public ResponseEntity<?> commitCursors(@PathVariable("subscriptionId") final String subscriptionId,
                                           @Valid @RequestBody final ItemsWrapper<SubscriptionCursor> cursors,
                                           final Errors errors,
                                           @NotNull @RequestHeader("X-Nakadi-StreamId") final String streamId,
                                           final NativeWebRequest request,
                                           final Client client) {

        LOG.debug("[COMMIT_CURSORS] committing {} cursor(s) for subscription {}", cursors.getItems().size(),
                subscriptionId);

        if (!featureToggleService.isFeatureEnabled(HIGH_LEVEL_API)) {
            return new ResponseEntity<>(NOT_IMPLEMENTED);
        }
        if (errors.hasErrors()) {
            return Responses.create(new ValidationProblem(errors), request);
        }

        try {
            validateSubscriptionReadScopes(client, subscriptionId);

            LOG.debug("[COMMIT_CURSORS] scopes validation finished");

            final List<CursorCommitResult> items = cursorsService.commitCursors(streamId, subscriptionId,
                    cursors.getItems());

            LOG.debug("[COMMIT_CURSORS] commit finished");

            final boolean allCommited = items.stream()
                    .allMatch(cursor -> CursorCommitResult.COMMITTED.equals(cursor.getResult()));
            final ItemsWrapper<CursorCommitResult> body = new ItemsWrapper<>(items);
            return allCommited ? noContent().build() : ok(body);
        } catch (final NoSuchEventTypeException e) {
            return create(Problem.valueOf(UNPROCESSABLE_ENTITY, e.getMessage()), request);
        } catch (final NakadiException e) {
            return create(e.asProblem(), request);
        } catch (final InvalidCursorException e) {
            return create(Problem.valueOf(UNPROCESSABLE_ENTITY, e.getMessage()), request);
        }
    }

    private void validateSubscriptionReadScopes(final Client client, final String subscriptionId)
            throws ServiceUnavailableException, NoSuchSubscriptionException {
        subscriptionRepository.getSubscription(subscriptionId)
                .getEventTypes().stream().map(Try.wrap(eventTypeRepository::findByName))
                .map(Try::getOrThrow)
                .forEach(eventType -> client.checkScopes(eventType.getReadScopes()));
    }

}
