/**
 * Google Apps Script backend for the shared weekly menu and shopping list.
 */

const SHEET_NAMES = {
  PROPOSALS: 'MenuPlan',
  INGREDIENTS: 'MenuIngredients',
  SHOPPING: 'ShoppingList'
};

const CONFIG = {
  propApiToken: 'MENU_API_TOKEN'
};

function doGet(e) {
  try {
    const params = e.parameter;
    validateToken(params.token);

    switch ((params.action || '').toLowerCase()) {
      case 'liststate':
        return jsonResponse(listState());
      default:
        return errorResponse('Unknown action', 400);
    }
  } catch (err) {
    return errorResponse(err.message, err.status || 500);
  }
}

function doPost(e) {
  try {
    const params = JSON.parse(e.postData.contents);
    validateToken(params.token);

    switch ((params.action || '').toLowerCase()) {
      case 'saveproposal':
        return jsonResponse(saveProposal(params.payload));
      case 'saveproposalwithingredients':
        return jsonResponse(saveProposalWithIngredients(params.payload));
      case 'deleteproposal':
        return jsonResponse(deleteProposal(params.payload));
      case 'saveingredient':
        return jsonResponse(saveIngredient(params.payload));
      case 'deleteingredient':
        return jsonResponse(deleteIngredient(params.payload));
      case 'saveshoppingitem':
        return jsonResponse(saveShoppingItem(params.payload));
      case 'deleteshoppingitem':
        return jsonResponse(deleteShoppingItem(params.payload));
      default:
        return errorResponse('Unknown action', 400);
    }
  } catch (err) {
    return errorResponse(err.message, err.status || 500);
  }
}

function validateToken(requestToken) {
  const token = PropertiesService.getScriptProperties().getProperty(CONFIG.propApiToken);
  if (!token) {
    throw createError('API token is not configured in Script Properties', 500);
  }
  if (!requestToken || requestToken !== token) {
    throw createError('Unauthorized', 401);
  }
}

function listState() {
  return {
    proposals: listProposals(),
    ingredients: listIngredients(),
    shopping: listShopping()
  };
}

function listProposals() {
  const sheet = getSheet(SHEET_NAMES.PROPOSALS);
  return getDataAsObjects(sheet);
}

function listIngredients() {
  const sheet = getSheet(SHEET_NAMES.INGREDIENTS);
  const rows = getDataAsObjects(sheet);
  return rows.map(row => ({
    ingredient_id: row.ingredient_id,
    proposal_id: row.proposal_id,
    name: row.name,
    need_to_buy: toBoolean(row.need_to_buy),
    updated_at: row.updated_at
  }));
}

function listShopping() {
  const sheet = getSheet(SHEET_NAMES.SHOPPING);
  return getDataAsObjects(sheet);
}

function saveProposal(payload) {
  saveProposalInternal(payload);
  return { ok: true };
}

function saveProposalInternal(payload, timestamp) {
  if (!payload || !payload.proposal_id) {
    throw createError('proposal_id is required', 400);
  }
  const sheet = getSheet(SHEET_NAMES.PROPOSALS);
  const data = getDataRangeValues(sheet);
  const headers = data[0];
  const idIndex = headers.indexOf('proposal_id');
  const rowIndex = data.findIndex((row, idx) => idx > 0 && row[idIndex] === payload.proposal_id);
  const now = timestamp || new Date().toISOString();
  payload.updated_at = now;
  if (rowIndex > -1) {
    updateRow(sheet, rowIndex + 1, headers, payload);
  } else {
    payload.created_at = payload.created_at || now;
    payload.created_by = payload.created_by || 'app';
    appendRow(sheet, headers, payload);
  }
}

function deleteProposal(payload) {
  if (!payload || !payload.proposal_id) {
    throw createError('proposal_id is required', 400);
  }
  const sheet = getSheet(SHEET_NAMES.PROPOSALS);
  const data = getDataRangeValues(sheet);
  const headers = data[0];
  const idIndex = headers.indexOf('proposal_id');
  const rowIndex = data.findIndex((row, idx) => idx > 0 && row[idIndex] === payload.proposal_id);
  if (rowIndex === -1) {
    throw createError('proposal not found', 404);
  }
  sheet.deleteRow(rowIndex + 1);

  const ingredientSheet = getSheet(SHEET_NAMES.INGREDIENTS);
  const ingredientData = getDataRangeValues(ingredientSheet);
  const ingredientHeaders = ingredientData[0];
  const proposalIndex = ingredientHeaders.indexOf('proposal_id');
  for (let i = ingredientData.length - 1; i > 0; i--) {
    if (ingredientData[i][proposalIndex] === payload.proposal_id) {
      ingredientSheet.deleteRow(i + 1);
    }
  }
  regenerateShoppingList();
  return { ok: true };
}

function saveIngredient(payload) {
  saveIngredientInternal(payload);
  regenerateShoppingList();
  return { ok: true };
}

function saveIngredientInternal(payload, timestamp) {
  if (!payload || !payload.ingredient_id) {
    throw createError('ingredient_id is required', 400);
  }
  if (!payload.proposal_id) {
    throw createError('proposal_id is required', 400);
  }
  const sheet = getSheet(SHEET_NAMES.INGREDIENTS);
  const data = getDataRangeValues(sheet);
  const headers = data[0];
  const idIndex = headers.indexOf('ingredient_id');
  const rowIndex = data.findIndex((row, idx) => idx > 0 && row[idIndex] === payload.ingredient_id);
  payload.need_to_buy = toBoolean(payload.need_to_buy);
  payload.updated_at = timestamp || new Date().toISOString();
  if (rowIndex > -1) {
    updateRow(sheet, rowIndex + 1, headers, payload);
  } else {
    appendRow(sheet, headers, payload);
  }
}

function saveProposalWithIngredients(payload) {
  if (!payload || !payload.proposal) {
    throw createError('proposal payload is required', 400);
  }
  const proposalPayload = payload.proposal;
  const ingredientPayloads = Array.isArray(payload.ingredients) ? payload.ingredients : [];
  const timestamp = new Date().toISOString();
  saveProposalInternal(proposalPayload, timestamp);
  ingredientPayloads.forEach(raw => {
    const ingredientPayload = Object.assign({}, raw);
    ingredientPayload.proposal_id = ingredientPayload.proposal_id || proposalPayload.proposal_id;
    saveIngredientInternal(ingredientPayload, timestamp);
  });
  regenerateShoppingList();
  return { ok: true };
}

function deleteIngredient(payload) {
  if (!payload || !payload.ingredient_id) {
    throw createError('ingredient_id is required', 400);
  }
  const sheet = getSheet(SHEET_NAMES.INGREDIENTS);
  const data = getDataRangeValues(sheet);
  const headers = data[0];
  const idIndex = headers.indexOf('ingredient_id');
  const rowIndex = data.findIndex((row, idx) => idx > 0 && row[idIndex] === payload.ingredient_id);
  if (rowIndex === -1) {
    throw createError('ingredient not found', 404);
  }
  sheet.deleteRow(rowIndex + 1);
  regenerateShoppingList();
  return { ok: true };
}

function saveShoppingItem(payload) {
  if (!payload || !payload.shopping_id) {
    throw createError('shopping_id is required', 400);
  }
  const sheet = getSheet(SHEET_NAMES.SHOPPING);
  const data = getDataRangeValues(sheet);
  const headers = data[0];
  const idIndex = headers.indexOf('shopping_id');
  const rowIndex = data.findIndex((row, idx) => idx > 0 && row[idIndex] === payload.shopping_id);
  const now = new Date().toISOString();
  const record = {
    shopping_id: payload.shopping_id,
    ingredient_id: payload.ingredient_id || '',
    proposal_id: payload.proposal_id || '',
    name: payload.name || '',
    status: payload.status || 'pending',
    updated_at: now
  };
  if (rowIndex > -1) {
    updateRow(sheet, rowIndex + 1, headers, record);
  } else {
    appendRow(sheet, headers, record);
  }
  return { ok: true };
}

function deleteShoppingItem(payload) {
  if (!payload || !payload.shopping_id) {
    throw createError('shopping_id is required', 400);
  }
  const sheet = getSheet(SHEET_NAMES.SHOPPING);
  const data = getDataRangeValues(sheet);
  const headers = data[0];
  const idIndex = headers.indexOf('shopping_id');
  const rowIndex = data.findIndex((row, idx) => idx > 0 && row[idIndex] === payload.shopping_id);
  if (rowIndex === -1) {
    throw createError('shopping item not found', 404);
  }
  sheet.deleteRow(rowIndex + 1);
  return { ok: true };
}

function regenerateShoppingList() {
  const ingredients = listIngredients();
  const shoppingSheet = getSheet(SHEET_NAMES.SHOPPING);
  const data = getDataRangeValues(shoppingSheet);
  if (!data.length) {
    return;
  }
  const headers = data[0];
  const headerIndex = buildHeaderIndex(headers);
  const manualRows = [];
  for (let i = 1; i < data.length; i++) {
    const row = data[i];
    if (!row[headerIndex.ingredient_id]) {
      manualRows.push(row);
    }
  }
  const rows = manualRows.slice();
  const now = new Date().toISOString();
  ingredients.forEach(ing => {
    if (ing.need_to_buy) {
      rows.push(payloadToRow(headers, {
        shopping_id: ing.ingredient_id,
        ingredient_id: ing.ingredient_id,
        proposal_id: ing.proposal_id,
        name: ing.name,
        status: 'pending',
        updated_at: now
      }));
    }
  });
  const nameIndex = headerIndex.name !== undefined ? headerIndex.name : headers.indexOf('name');
  if (nameIndex >= 0) {
    rows.sort((a, b) => {
      const left = (a[nameIndex] || '').toString().trim().toLowerCase();
      const right = (b[nameIndex] || '').toString().trim().toLowerCase();
      if (left === right) {
        return 0;
      }
      if (!left) {
        return 1;
      }
      if (!right) {
        return -1;
      }
      return left.localeCompare(right);
    });
  }
  const lastRow = shoppingSheet.getLastRow();
  if (lastRow > 1) {
    shoppingSheet.getRange(2, 1, lastRow - 1, headers.length).clearContent();
  }
  if (rows.length > 0) {
    shoppingSheet.getRange(2, 1, rows.length, headers.length).setValues(rows);
  }
}

function getSheet(name) {
  const sheet = SpreadsheetApp.getActive().getSheetByName(name);
  if (!sheet) {
    throw createError("Sheet '" + name + "' not found", 500);
  }
  return sheet;
}

function getDataRangeValues(sheet) {
  const range = sheet.getDataRange();
  const values = range.getValues();
  if (values.length === 0) {
    return [[]];
  }
  return values;
}

function getDataAsObjects(sheet) {
  const values = getDataRangeValues(sheet);
  if (values.length < 2) {
    return [];
  }
  const headers = values[0];
  const rows = values.slice(1);
  return rows
    .filter(row => row.some(cell => cell !== ''))
    .map(row => asObject(headers, row));
}

function appendRow(sheet, headers, payload) {
  sheet.appendRow(payloadToRow(headers, payload));
}

function updateRow(sheet, rowNumber, headers, payload) {
  const row = headers.map(header => (payload[header] !== undefined ? payload[header] : sheet.getRange(rowNumber, headers.indexOf(header) + 1).getValue()));
  sheet.getRange(rowNumber, 1, 1, row.length).setValues([row]);
}

function clearSheetExceptHeaders(sheet) {
  const lastRow = sheet.getLastRow();
  if (lastRow > 1) {
    sheet.getRange(2, 1, lastRow - 1, sheet.getLastColumn()).clearContent();
  }
}

function asObject(headers, row) {
  const obj = {};
  headers.forEach((header, idx) => {
    obj[header] = row[idx];
  });
  return obj;
}

function toBoolean(value) {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'number') {
    return value !== 0;
  }
  if (typeof value === 'string') {
    return value.toLowerCase() === 'true' || value === '1';
  }
  return false;
}

function jsonResponse(payload) {
  return ContentService.createTextOutput(JSON.stringify(payload)).setMimeType(ContentService.MimeType.JSON);
}

function errorResponse(message, status) {
  return ContentService.createTextOutput(JSON.stringify({ error: message, status })).setMimeType(ContentService.MimeType.JSON);
}

function createError(message, status) {
  const err = new Error(message);
  err.status = status;
  return err;
}

function buildHeaderIndex(headers) {
  return headers.reduce((acc, header, idx) => {
    acc[header] = idx;
    return acc;
  }, {});
}

function payloadToRow(headers, payload) {
  return headers.map(header => (payload[header] !== undefined ? payload[header] : ''));
}
