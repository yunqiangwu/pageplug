import { ReduxActionTypes, ReduxAction } from "constants/ReduxActionConstants";
import { RestAction } from "api/ActionAPI";
import { ActionWidgetIdsMap } from "sagas/ActionWidgetMapSagas";

export const createActionRequest = (payload: Partial<RestAction>) => {
  return {
    type: ReduxActionTypes.CREATE_ACTION_INIT,
    payload,
  };
};

export const createActionSuccess = (payload: RestAction) => {
  return {
    type: ReduxActionTypes.CREATE_ACTION_SUCCESS,
    payload,
  };
};

export type FetchActionsPayload = {
  pageId: string;
};

export const fetchActions = (
  payload: FetchActionsPayload,
): ReduxAction<FetchActionsPayload> => {
  return {
    type: ReduxActionTypes.FETCH_ACTIONS_INIT,
    payload,
  };
};

export const runApiAction = (id: string) => {
  return {
    type: ReduxActionTypes.RUN_API_REQUEST,
    payload: id,
  };
};

export const updateAction = (payload: { data: RestAction }) => {
  return {
    type: ReduxActionTypes.UPDATE_ACTION_INIT,
    payload,
  };
};

export const updateActionSuccess = (payload: { data: RestAction }) => {
  return {
    type: ReduxActionTypes.UPDATE_ACTION_SUCCESS,
    payload,
  };
};

export const deleteAction = (payload: { id: string }) => {
  return {
    type: ReduxActionTypes.DELETE_ACTION_INIT,
    payload,
  };
};

export const deleteActionSuccess = (payload: { id: string }) => {
  return {
    type: ReduxActionTypes.DELETE_ACTION_SUCCESS,
    payload,
  };
};

export const actionToWidgetIdMapSuccess = (
  map: ActionWidgetIdsMap,
): ReduxAction<ActionWidgetIdsMap> => ({
  type: ReduxActionTypes.CREATE_UPDATE_ACTION_WIDGETIDS_MAP_SUCCESS,
  payload: map,
});

export default {
  createAction: createActionRequest,
  fetchActions,
  runAction: runApiAction,
  deleteAction,
  deleteActionSuccess,
  updateAction,
  updateActionSuccess,
};
