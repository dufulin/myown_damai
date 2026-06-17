<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const tokenKey = 'damai_user_token'
const apiBase = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8080'
const token = ref(localStorage.getItem(tokenKey) || '')
const currentUser = ref(null)
const mode = ref('login')
const loading = ref(false)
const programLoading = ref(false)
const homeProgramLoading = ref(false)
const detailLoading = ref(false)
const orderLoading = ref(false)
const orderListLoading = ref(false)
const programCategories = ref([])
const homePrograms = ref([])
const programs = ref([])
const orders = ref([])
const ticketUsers = ref([])
const selectedProgramDetail = ref(null)
const viewMode = ref('home')
const searchKeyword = ref('')
const pageNumber = ref(1)
const orderPageNumber = ref(1)
const pageSize = 6
const orderPageSize = 8
const homePageSize = 10
const programSheetOpen = ref(false)
const notice = reactive({ type: 'info', text: `Backend API: ${apiBase}` })
const activeFilter = reactive({
  label: 'All Programs',
  categoryId: null,
  areaId: null,
  keyword: ''
})

const form = reactive({
  login: '',
  password: '',
  name: '',
  mobile: '',
  email: ''
})

const orderForm = reactive({
  showTimeId: '',
  ticketCategoryId: '',
  ticketUserIds: []
})

const ticketUserForm = reactive({
  relName: '',
  idType: 1,
  idNumber: ''
})

const areaOptions = [
  { id: null, label: 'All Areas' },
  { id: 110000, label: 'Beijing' },
  { id: 310000, label: 'Shanghai' },
  { id: 440100, label: 'Guangzhou' },
  { id: 440300, label: 'Shenzhen' },
  { id: 330100, label: 'Hangzhou' },
  { id: 510100, label: 'Chengdu' }
]

const fallbackCategories = [
  { id: null, name: 'All Types', parentId: 0, type: 1, keyword: '' },
  { id: null, name: 'Concerts', parentId: 0, type: 1, keyword: 'Concert' },
  { id: null, name: 'Drama', parentId: 0, type: 1, keyword: 'Drama' },
  { id: null, name: 'Sports', parentId: 0, type: 1, keyword: 'Sports' },
  { id: null, name: 'Family', parentId: 0, type: 1, keyword: 'Family' }
]

const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))
const title = computed(() => (mode.value === 'login' ? 'Login' : 'Register'))
const hasNextPage = computed(() => programs.value.length === pageSize)
const hasNextOrderPage = computed(() => orders.value.length === orderPageSize)
const selectedProgram = computed(() => selectedProgramDetail.value?.program || null)
const showTimes = computed(() => selectedProgramDetail.value?.showTimes || [])
const ticketCategories = computed(() => selectedProgramDetail.value?.ticketCategories || [])
const selectedShowTime = computed(() => showTimes.value.find((showTime) => String(showTime.id) === String(orderForm.showTimeId)) || null)
const selectedTicketCategory = computed(() => ticketCategories.value.find((ticket) => String(ticket.id) === String(orderForm.ticketCategoryId)) || null)
const orderTotal = computed(() => {
  const price = Number(selectedTicketCategory.value?.price || 0)
  return (price * orderForm.ticketUserIds.length).toFixed(2)
})
const canCreateOrder = computed(() => Boolean(selectedProgram.value && selectedShowTime.value && selectedTicketCategory.value && orderForm.ticketUserIds.length > 0))
const visibleCategories = computed(() => {
  const parents = programCategories.value.filter((category) => category.parentId === 0 || category.type === 1)
  return parents.length > 0 ? [{ id: null, name: 'All Types', keyword: '' }, ...parents] : fallbackCategories
})
const noticeItems = computed(() => {
  const notices = selectedProgramDetail.value?.notices || {}
  return [
    { label: 'Important Notice', value: notices.importantNotice },
    { label: 'Pre-sale', value: notices.preSellInstruction },
    { label: 'Refund Rule', value: notices.refundTicketRule },
    { label: 'Delivery', value: notices.deliveryInstruction },
    { label: 'Entry Rule', value: notices.entryRule },
    { label: 'Child Ticket', value: notices.childPurchase },
    { label: 'Invoice', value: notices.invoiceSpecification },
    { label: 'Real-name Rule', value: notices.realTicketPurchaseRule },
    { label: 'Abnormal Order', value: notices.abnormalOrderDescription },
    { label: 'Reminder', value: notices.kindReminder },
    { label: 'Duration', value: notices.performanceDuration },
    { label: 'Entry Time', value: notices.entryTime },
    { label: 'Refund Explain', value: notices.refundExplain },
    { label: 'Seat Selection', value: notices.chooseSeatExplain }
  ].filter((item) => item.value)
})

/**
 * Updates the global notice message.
 */
function setNotice(type, text) {
  notice.type = type
  notice.text = text
}

/**
 * Stores or clears the login token.
 */
function saveToken(nextToken) {
  token.value = nextToken || ''
  if (nextToken) {
    localStorage.setItem(tokenKey, nextToken)
    return
  }
  localStorage.removeItem(tokenKey)
}

/**
 * Sends a JSON request to the backend API.
 */
async function request(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  }

  if (token.value) {
    headers.Authorization = `Bearer ${token.value}`
  }

  const response = await fetch(`${apiBase}${path}`, { ...options, headers })
  const payload = await response.json().catch(() => null)

  if (!response.ok) {
    throw new Error(payload?.message || `Request failed: ${response.status}`)
  }
  return payload
}

/**
 * Formats date and time values for compact display.
 */
function formatDateTime(value) {
  if (!value) {
    return 'Pending'
  }
  return new Date(value).toLocaleString()
}

/**
 * Converts an order status code to user-facing text.
 */
function orderStatusText(status) {
  const statusMap = {
    1: 'Unpaid',
    2: 'Canceled',
    3: 'Paid',
    4: 'Refunded'
  }
  return statusMap[status] || 'Unknown'
}

/**
 * Builds a CSS class for the order status pill.
 */
function orderStatusClass(status) {
  return `order-status status-${status || 'unknown'}`
}

/**
 * Returns stable summary text for a program card.
 */
function programMeta(program) {
  const actor = program.actor || 'Artist pending'
  const place = program.place || 'Venue pending'
  return `${actor} / ${place}`
}

/**
 * Submits the login or registration form.
 */
async function submit() {
  loading.value = true
  try {
    const path = mode.value === 'login' ? '/api/users/login' : '/api/users/register'
    const body = mode.value === 'login'
      ? { login: form.login, password: form.password }
      : { name: form.name, password: form.password, mobile: form.mobile, email: form.email }

    const result = await request(path, {
      method: 'POST',
      body: JSON.stringify(body)
    })

    saveToken(result.data.token)
    currentUser.value = result.data.user
    setNotice('success', mode.value === 'login' ? 'Login succeeded.' : 'Registered and logged in.')
    await initializeHome()
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    loading.value = false
  }
}

/**
 * Restores the current user from a saved token.
 */
async function loadCurrentUser() {
  if (!token.value) {
    return
  }
  loading.value = true
  try {
    const result = await request('/api/users/me')
    currentUser.value = result.data
    setNotice('success', 'Session restored.')
    await initializeHome()
  } catch (error) {
    saveToken('')
    currentUser.value = null
    setNotice('error', error.message)
  } finally {
    loading.value = false
  }
}

/**
 * Loads homepage categories and the first ten programs after login.
 */
async function initializeHome() {
  viewMode.value = 'home'
  await Promise.all([loadProgramCategories(), loadHomePrograms(), loadTicketUsers()])
}

/**
 * Loads backend program categories for the type menu.
 */
async function loadProgramCategories() {
  try {
    const result = await request('/api/programs/categories')
    programCategories.value = result.data || []
  } catch (error) {
    programCategories.value = []
    setNotice('error', error.message)
  }
}

/**
 * Loads ten programs for the homepage default view.
 */
async function loadHomePrograms() {
  homeProgramLoading.value = true
  try {
    const params = new URLSearchParams({
      pageNumber: '1',
      pageSize: String(homePageSize)
    })
    const result = await request(`/api/programs?${params.toString()}`)
    homePrograms.value = result.data || []
  } catch (error) {
    homePrograms.value = []
    setNotice('error', error.message)
  } finally {
    homeProgramLoading.value = false
  }
}

/**
 * Logs the user out and clears the homepage state.
 */
async function logout() {
  loading.value = true
  try {
    await request('/api/users/logout', { method: 'POST' })
    setNotice('success', 'Logged out.')
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    saveToken('')
    currentUser.value = null
    programSheetOpen.value = false
    programs.value = []
    orders.value = []
    ticketUsers.value = []
    homePrograms.value = []
    selectedProgramDetail.value = null
    viewMode.value = 'home'
    loading.value = false
  }
}

/**
 * Switches between login and registration forms.
 */
function switchMode(nextMode) {
  mode.value = nextMode
  setNotice('info', nextMode === 'login' ? 'Enter mobile/email credentials.' : 'Create a new account.')
}

/**
 * Opens the program list by program type.
 */
async function openTypePrograms(category) {
  pageNumber.value = 1
  activeFilter.label = category.name || 'All Types'
  activeFilter.categoryId = category.id
  activeFilter.areaId = null
  activeFilter.keyword = category.id ? '' : (category.keyword || '')
  programSheetOpen.value = true
  await loadPrograms()
}

/**
 * Opens the program list by area.
 */
async function openAreaPrograms(area) {
  pageNumber.value = 1
  activeFilter.label = area.label
  activeFilter.categoryId = null
  activeFilter.areaId = area.id
  activeFilter.keyword = ''
  programSheetOpen.value = true
  await loadPrograms()
}

/**
 * Searches programs from the homepage search box.
 */
async function searchPrograms() {
  const keyword = searchKeyword.value.trim()
  pageNumber.value = 1
  activeFilter.label = keyword ? `Search: ${keyword}` : 'All Programs'
  activeFilter.categoryId = null
  activeFilter.areaId = null
  activeFilter.keyword = keyword
  programSheetOpen.value = true
  await loadPrograms()
}

/**
 * Loads one page of programs from the backend.
 */
async function loadPrograms() {
  programLoading.value = true
  try {
    const params = new URLSearchParams()
    params.set('pageNumber', String(pageNumber.value))
    params.set('pageSize', String(pageSize))
    if (activeFilter.keyword) {
      params.set('keyword', activeFilter.keyword)
    }
    if (activeFilter.categoryId) {
      params.set('categoryId', String(activeFilter.categoryId))
    }
    if (activeFilter.areaId) {
      params.set('areaId', String(activeFilter.areaId))
    }
    const result = await request(`/api/programs?${params.toString()}`)
    programs.value = result.data || []
  } catch (error) {
    programs.value = []
    setNotice('error', error.message)
  } finally {
    programLoading.value = false
  }
}

/**
 * Opens the detail page for one selected program.
 */
async function openProgramDetail(programId) {
  detailLoading.value = true
  try {
    const result = await request(`/api/programs/${programId}`)
    selectedProgramDetail.value = result.data
    resetOrderForm()
    programSheetOpen.value = false
    viewMode.value = 'detail'
    setNotice('success', 'Program detail loaded.')
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    detailLoading.value = false
  }
}

/**
 * Selects default order options from the loaded program detail.
 */
function resetOrderForm() {
  orderForm.showTimeId = showTimes.value[0]?.id ? String(showTimes.value[0].id) : ''
  orderForm.ticketCategoryId = ticketCategories.value[0]?.id ? String(ticketCategories.value[0].id) : ''
  orderForm.ticketUserIds = ticketUsers.value[0]?.id ? [String(ticketUsers.value[0].id)] : []
}

/**
 * Creates an unpaid order from the selected show time and ticket category.
 */
async function createOrder() {
  if (!canCreateOrder.value) {
    setNotice('error', '请选择演出时间、票档和购票人。')
    return
  }

  orderLoading.value = true
  try {
    const orderTicketUsers = orderForm.ticketUserIds.map((ticketUserId) => ({
      ticketUserId: Number(ticketUserId),
      seatId: null,
      // Seat choice is not implemented yet, so the backend receives a stable placeholder.
      seatInfo: '未选择座位',
      ticketCategoryId: Number(orderForm.ticketCategoryId),
      orderPrice: selectedTicketCategory.value.price
    }))
    const body = {
      programId: selectedProgram.value.id,
      programItemPicture: selectedProgram.value.itemPicture,
      userId: currentUser.value.id,
      programTitle: selectedProgram.value.title,
      programPlace: selectedProgram.value.place,
      programShowTime: selectedShowTime.value.showTime,
      programPermitChooseSeat: selectedProgram.value.permitChooseSeat || 0,
      distributionMode: '电子票',
      takeTicketMode: '线上取票',
      payOrderType: 1,
      ticketUsers: orderTicketUsers
    }
    const result = await request('/api/orders', {
      method: 'POST',
      body: JSON.stringify(body)
    })
    setNotice('success', `订单创建成功：${result.data.orderNumber}`)
    await loadOrders()
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    orderLoading.value = false
  }
}

/**
 * Loads ticket buyers for the current user.
 */
async function loadTicketUsers() {
  if (!currentUser.value?.id) {
    return
  }

  try {
    const result = await request('/api/users/ticket-users')
    ticketUsers.value = result.data || []
  } catch (error) {
    ticketUsers.value = []
    setNotice('error', error.message)
  }
}

/**
 * Creates a real-name ticket buyer in the user service.
 */
async function createTicketUser() {
  orderListLoading.value = true
  try {
    const result = await request('/api/users/ticket-users', {
      method: 'POST',
      body: JSON.stringify({
        relName: ticketUserForm.relName,
        idType: Number(ticketUserForm.idType),
        idNumber: ticketUserForm.idNumber
      })
    })
    ticketUsers.value = [result.data, ...ticketUsers.value]
    ticketUserForm.relName = ''
    ticketUserForm.idType = 1
    ticketUserForm.idNumber = ''
    setNotice('success', `购票人已添加：${result.data.relName}`)
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    orderListLoading.value = false
  }
}

/**
 * Deletes one real-name ticket buyer and removes it from local selection.
 */
async function deleteTicketUser(ticketUserId) {
  orderListLoading.value = true
  try {
    await request(`/api/users/ticket-users/${ticketUserId}`, { method: 'DELETE' })
    ticketUsers.value = ticketUsers.value.filter((ticketUser) => ticketUser.id !== ticketUserId)
    orderForm.ticketUserIds = orderForm.ticketUserIds.filter((id) => String(id) !== String(ticketUserId))
    setNotice('success', '购票人已删除。')
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    orderListLoading.value = false
  }
}

/**
 * Opens the profile center and loads the current user's orders.
 */
async function openProfileCenter() {
  viewMode.value = 'profile'
  programSheetOpen.value = false
  selectedProgramDetail.value = null
  orderPageNumber.value = 1
  await Promise.all([loadOrders(), loadTicketUsers()])
}

/**
 * Loads one page of orders for the current user.
 */
async function loadOrders() {
  if (!currentUser.value?.id) {
    return
  }

  orderListLoading.value = true
  try {
    const params = new URLSearchParams({
      userId: String(currentUser.value.id),
      pageNumber: String(orderPageNumber.value),
      pageSize: String(orderPageSize)
    })
    const result = await request(`/api/orders?${params.toString()}`)
    orders.value = result.data || []
  } catch (error) {
    orders.value = []
    setNotice('error', error.message)
  } finally {
    orderListLoading.value = false
  }
}

/**
 * Cancels one unpaid order and refreshes the order list.
 */
async function cancelOrder(orderNumber) {
  orderListLoading.value = true
  try {
    await request(`/api/orders/${orderNumber}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason: 'Canceled by user' })
    })
    setNotice('success', `订单已取消：${orderNumber}`)
    await loadOrders()
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    orderListLoading.value = false
  }
}

/**
 * Creates an Alipay payment form and submits it in a new browser window.
 */
async function payOrder(order) {
  orderListLoading.value = true
  try {
    const result = await request('/api/pay/alipay/page-pay', {
      method: 'POST',
      body: JSON.stringify({
        orderNumber: order.orderNumber,
        userId: currentUser.value.id
      })
    })
    setNotice('success', `模拟支付成功：${result.data.payNumber}`)
    await loadOrders()
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    orderListLoading.value = false
  }
}

/**
 * Moves the order list to the previous page.
 */
async function previousOrderPage() {
  if (orderPageNumber.value <= 1) {
    return
  }
  orderPageNumber.value -= 1
  await loadOrders()
}

/**
 * Moves the order list to the next page.
 */
async function nextOrderPage() {
  if (!hasNextOrderPage.value) {
    return
  }
  orderPageNumber.value += 1
  await loadOrders()
}

/**
 * Returns from the detail page to the homepage.
 */
function backToHome() {
  selectedProgramDetail.value = null
  viewMode.value = 'home'
}

/**
 * Moves the program list to the previous page.
 */
async function previousPage() {
  if (pageNumber.value <= 1) {
    return
  }
  pageNumber.value -= 1
  await loadPrograms()
}

/**
 * Moves the program list to the next page.
 */
async function nextPage() {
  if (!hasNextPage.value) {
    return
  }
  pageNumber.value += 1
  await loadPrograms()
}

/**
 * Closes the program list sheet.
 */
function closeProgramSheet() {
  programSheetOpen.value = false
}

onMounted(loadCurrentUser)
</script>

<template>
  <main class="shell">
    <section class="topbar">
      <div>
        <p class="eyebrow">DAMAI TICKET OPS</p>
        <h1>{{ isLoggedIn ? 'Program Home' : 'Damai Ticket Management' }}</h1>
      </div>
      <div v-if="isLoggedIn" class="topbar-actions">
        <button class="ghost-button" type="button" :disabled="orderListLoading" @click="openProfileCenter">
          个人中心
        </button>
        <button class="ghost-button" :disabled="loading" @click="logout">
          Logout
        </button>
      </div>
    </section>

    <section v-if="!isLoggedIn" class="workspace">
      <form class="auth-panel" @submit.prevent="submit">
        <div class="tabs" aria-label="User actions">
          <button type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">
            Login
          </button>
          <button type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">
            Register
          </button>
        </div>

        <h2>{{ title }}</h2>
        <template v-if="mode === 'login'">
          <label>
            <span>Mobile or Email</span>
            <input v-model.trim="form.login" autocomplete="username" required maxlength="191" />
          </label>
        </template>

        <template v-else>
          <label>
            <span>Name</span>
            <input v-model.trim="form.name" maxlength="256" />
          </label>
          <label>
            <span>Mobile</span>
            <input v-model.trim="form.mobile" autocomplete="tel" required maxlength="191" />
          </label>
          <label>
            <span>Email</span>
            <input v-model.trim="form.email" type="email" autocomplete="email" maxlength="191" />
          </label>
        </template>

        <label>
          <span>Password</span>
          <input v-model="form.password" type="password" autocomplete="current-password" required minlength="6" maxlength="64" />
        </label>

        <button class="primary-button" type="submit" :disabled="loading">
          {{ loading ? 'Processing...' : title }}
        </button>
      </form>

      <aside class="status-panel">
        <div class="status-header">
          <p class="eyebrow">CURRENT USER</p>
          <span class="status-dot offline"></span>
        </div>

        <div class="empty-state">
          <strong>Not logged in</strong>
          <p>Log in to enter the program homepage.</p>
        </div>

        <p :class="['notice', notice.type]">{{ notice.text }}</p>
      </aside>
    </section>

    <section v-else-if="viewMode === 'detail' && selectedProgram" class="detail-layout">
      <div class="detail-toolbar">
        <button class="ghost-button" type="button" @click="backToHome">
          Back
        </button>
        <p :class="['notice', notice.type]">{{ notice.text }}</p>
      </div>

      <section class="detail-hero">
        <div class="poster detail-poster">
          <img v-if="selectedProgram.itemPicture" :src="selectedProgram.itemPicture" alt="" />
          <span v-else>{{ selectedProgram.title.slice(0, 2) }}</span>
        </div>
        <div>
          <p class="eyebrow">PROGRAM DETAIL</p>
          <h2>{{ selectedProgram.title }}</h2>
          <p>{{ programMeta(selectedProgram) }}</p>
          <dl class="meta-grid">
            <div>
              <dt>Area</dt>
              <dd>{{ selectedProgram.areaId }}</dd>
            </div>
            <div>
              <dt>Issue Time</dt>
              <dd>{{ formatDateTime(selectedProgram.issueTime) }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <section class="detail-grid">
        <article class="detail-section">
          <p class="eyebrow">INTRODUCTION</p>
          <p class="detail-text">{{ selectedProgramDetail.detail || 'No introduction available.' }}</p>
        </article>

        <article class="detail-section">
          <p class="eyebrow">SHOW TIMES</p>
          <div v-if="showTimes.length === 0" class="muted">No show times available.</div>
          <div v-else class="showtime-list">
            <div v-for="showTime in showTimes" :key="showTime.id" class="showtime-item">
              <strong>{{ formatDateTime(showTime.showTime) }}</strong>
              <span>{{ showTime.showWeekTime || 'Week pending' }}</span>
            </div>
          </div>
        </article>

        <article class="detail-section wide">
          <p class="eyebrow">TICKET CATEGORIES</p>
          <div v-if="ticketCategories.length === 0" class="muted">No ticket categories available.</div>
          <div v-else class="ticket-grid">
            <div v-for="ticket in ticketCategories" :key="ticket.id" class="ticket-card">
              <strong>{{ ticket.introduce }}</strong>
              <span>CNY {{ ticket.price }}</span>
              <small>{{ ticket.remainNumber }} / {{ ticket.totalNumber }} remaining</small>
            </div>
          </div>
        </article>

        <article class="detail-section wide">
          <p class="eyebrow">下单</p>
          <div class="order-form">
            <label>
              <span>演出时间</span>
              <select v-model="orderForm.showTimeId">
                <option value="" disabled>请选择演出时间</option>
                <option v-for="showTime in showTimes" :key="showTime.id" :value="String(showTime.id)">
                  {{ formatDateTime(showTime.showTime) }} {{ showTime.showWeekTime || '' }}
                </option>
              </select>
            </label>
            <label>
              <span>票档</span>
              <select v-model="orderForm.ticketCategoryId">
                <option value="" disabled>请选择票档</option>
                <option v-for="ticket in ticketCategories" :key="ticket.id" :value="String(ticket.id)">
                  {{ ticket.introduce }} / CNY {{ ticket.price }}
                </option>
              </select>
            </label>
            <label>
              <span>购票人</span>
              <select v-model="orderForm.ticketUserIds" multiple>
                <option v-for="ticketUser in ticketUsers" :key="ticketUser.id" :value="String(ticketUser.id)">
                  {{ ticketUser.relName }} / {{ ticketUser.idNumber }}
                </option>
              </select>
            </label>
            <div class="order-total">
              <span>合计</span>
              <strong>CNY {{ orderTotal }}</strong>
            </div>
            <button class="primary-button" type="button" :disabled="orderLoading || !canCreateOrder" @click="createOrder">
              {{ orderLoading ? '下单中...' : '下单' }}
            </button>
          </div>
          <p v-if="ticketUsers.length === 0" class="muted">请先到个人中心添加购票人，再创建订单。</p>
        </article>

        <article class="detail-section wide">
          <p class="eyebrow">PURCHASE NOTICE</p>
          <div v-if="noticeItems.length === 0" class="muted">No purchase notices available.</div>
          <div v-else class="notice-grid">
            <div v-for="item in noticeItems" :key="item.label" class="notice-item">
              <strong>{{ item.label }}</strong>
              <p>{{ item.value }}</p>
            </div>
          </div>
        </article>
      </section>
    </section>

    <section v-else-if="viewMode === 'profile'" class="profile-layout">
      <div class="detail-toolbar">
        <button class="ghost-button" type="button" @click="initializeHome">
          Back
        </button>
        <p :class="['notice', notice.type]">{{ notice.text }}</p>
      </div>

      <section class="profile-strip">
        <div>
          <p class="eyebrow">个人中心</p>
          <strong>{{ currentUser.name || currentUser.username || currentUser.mobile }}</strong>
        </div>
        <dl>
          <div>
            <dt>ID</dt>
            <dd>{{ currentUser.id }}</dd>
          </div>
          <div>
            <dt>Mobile</dt>
            <dd>{{ currentUser.mobile || currentUser.phone || 'Not set' }}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{{ currentUser.email || 'Not set' }}</dd>
          </div>
        </dl>
      </section>

      <section class="order-section">
        <div class="featured-header">
          <div>
            <p class="eyebrow">购票人</p>
            <h2>我的购票人</h2>
          </div>
          <button class="ghost-button" type="button" :disabled="orderListLoading" @click="loadTicketUsers">
            刷新
          </button>
        </div>

        <form class="ticket-user-form" @submit.prevent="createTicketUser">
          <label>
            <span>姓名</span>
            <input v-model.trim="ticketUserForm.relName" required maxlength="256" />
          </label>
          <label>
            <span>证件类型</span>
            <select v-model.number="ticketUserForm.idType">
              <option :value="1">身份证</option>
              <option :value="2">港澳台居民居住证</option>
              <option :value="3">港澳居民来往内地通行证</option>
              <option :value="4">台湾居民来往内地通行证</option>
              <option :value="5">护照</option>
              <option :value="6">外国人永久居住证</option>
            </select>
          </label>
          <label>
            <span>证件号码</span>
            <input v-model.trim="ticketUserForm.idNumber" required maxlength="512" />
          </label>
          <button class="primary-button" type="submit" :disabled="orderListLoading">
            添加购票人
          </button>
        </form>

        <div v-if="ticketUsers.length === 0" class="empty-state sheet-state">
          <strong>暂无购票人</strong>
          <p>添加购票人后，下单时即可选择。</p>
        </div>
        <div v-else class="ticket-user-list">
          <article v-for="ticketUser in ticketUsers" :key="ticketUser.id" class="ticket-user-card">
            <div>
              <strong>{{ ticketUser.relName }}</strong>
              <p>证件类型 {{ ticketUser.idType }} / {{ ticketUser.idNumber }}</p>
            </div>
            <button class="ghost-button" type="button" :disabled="orderListLoading" @click="deleteTicketUser(ticketUser.id)">
              删除
            </button>
          </article>
        </div>
      </section>

      <section class="order-section">
        <div class="featured-header">
          <div>
            <p class="eyebrow">订单</p>
            <h2>我的订单</h2>
          </div>
          <button class="ghost-button" type="button" :disabled="orderListLoading" @click="loadOrders">
            刷新
          </button>
        </div>

        <div v-if="orderListLoading" class="empty-state sheet-state">
          <strong>订单加载中</strong>
          <p>正在获取你的最新订单。</p>
        </div>
        <div v-else-if="orders.length === 0" class="empty-state sheet-state">
          <strong>暂无订单</strong>
          <p>可以从节目详情页选择票档后下单。</p>
        </div>
        <div v-else class="order-list">
          <article v-for="order in orders" :key="order.orderNumber" class="order-card">
            <div class="order-card-main">
              <div>
                <p class="eyebrow">NO. {{ order.orderNumber }}</p>
                <h3>{{ order.programTitle }}</h3>
                <p>{{ order.programPlace || 'Venue pending' }} / {{ formatDateTime(order.programShowTime) }}</p>
              </div>
              <span :class="orderStatusClass(order.orderStatus)">
                {{ orderStatusText(order.orderStatus) }}
              </span>
            </div>
            <dl class="order-meta">
              <div>
                <dt>Amount</dt>
                <dd>CNY {{ order.orderPrice }}</dd>
              </div>
              <div>
                <dt>Created</dt>
                <dd>{{ formatDateTime(order.createOrderTime) }}</dd>
              </div>
              <div>
                <dt>Expires</dt>
                <dd>{{ formatDateTime(order.expireTime) }}</dd>
              </div>
            </dl>
            <div class="order-ticket-list">
              <span v-for="ticket in order.ticketUsers" :key="ticket.id">
                票档 #{{ ticket.ticketCategoryId }} / CNY {{ ticket.orderPrice }}
              </span>
            </div>
            <div class="order-actions">
              <button
                class="primary-button"
                type="button"
                :disabled="orderListLoading || order.orderStatus !== 1"
                @click="payOrder(order)"
              >
                支付宝支付
              </button>
              <button
                class="ghost-button"
                type="button"
                :disabled="orderListLoading || order.orderStatus !== 1"
                @click="cancelOrder(order.orderNumber)"
              >
                取消订单
              </button>
            </div>
          </article>
        </div>

        <div class="pagination">
          <button class="ghost-button" type="button" :disabled="orderPageNumber <= 1 || orderListLoading" @click="previousOrderPage">
            Previous
          </button>
          <span>Page {{ orderPageNumber }}</span>
          <button class="ghost-button" type="button" :disabled="!hasNextOrderPage || orderListLoading" @click="nextOrderPage">
            Next
          </button>
        </div>
      </section>
    </section>

    <section v-else class="home-layout">
      <section class="search-band">
        <div>
          <p class="eyebrow">DISCOVER PROGRAMS</p>
          <h2>Search by keyword, type, or area</h2>
        </div>
        <form class="search-row" @submit.prevent="searchPrograms">
          <input v-model.trim="searchKeyword" placeholder="Program name, artist, or keyword" maxlength="80" />
          <button class="primary-button" type="submit" :disabled="programLoading">
            Search
          </button>
        </form>
      </section>

      <section class="menu-grid">
        <div class="menu-section">
          <div class="section-heading">
            <p class="eyebrow">BY TYPE</p>
            <h2>Type Menu</h2>
          </div>
          <div class="chip-grid">
            <button
              v-for="category in visibleCategories"
              :key="`category-${category.id || category.name}`"
              type="button"
              class="chip"
              @click="openTypePrograms(category)"
            >
              {{ category.name }}
            </button>
          </div>
        </div>

        <div class="menu-section">
          <div class="section-heading">
            <p class="eyebrow">BY AREA</p>
            <h2>Area Menu</h2>
          </div>
          <div class="chip-grid">
            <button
              v-for="area in areaOptions"
              :key="`area-${area.id || area.label}`"
              type="button"
              class="chip"
              @click="openAreaPrograms(area)"
            >
              {{ area.label }}
            </button>
          </div>
        </div>
      </section>

      <section class="featured-section">
        <div class="featured-header">
          <div>
            <p class="eyebrow">FEATURED</p>
            <h2>Latest 10 Programs</h2>
          </div>
          <button class="ghost-button" type="button" :disabled="homeProgramLoading" @click="loadHomePrograms">
            Refresh
          </button>
        </div>

        <div v-if="homeProgramLoading" class="empty-state sheet-state">
          <strong>Loading programs</strong>
          <p>Fetching the latest database records.</p>
        </div>
        <div v-else-if="homePrograms.length === 0" class="empty-state sheet-state">
          <strong>No programs yet</strong>
          <p>Create or import program data, then refresh this homepage.</p>
        </div>
        <div v-else class="program-card-grid">
          <button
            v-for="program in homePrograms"
            :key="program.id"
            type="button"
            class="program-card"
            @click="openProgramDetail(program.id)"
          >
            <div class="poster">
              <img v-if="program.itemPicture" :src="program.itemPicture" alt="" />
              <span v-else>{{ program.title.slice(0, 2) }}</span>
            </div>
            <div class="program-card-body">
              <h3>{{ program.title }}</h3>
              <p>{{ programMeta(program) }}</p>
              <small>{{ formatDateTime(program.issueTime) }}</small>
            </div>
          </button>
        </div>
      </section>

      <section class="profile-strip">
        <div>
          <p class="eyebrow">CURRENT USER</p>
          <strong>{{ currentUser.name || currentUser.username || currentUser.mobile }}</strong>
        </div>
        <dl>
          <div>
            <dt>ID</dt>
            <dd>{{ currentUser.id }}</dd>
          </div>
          <div>
            <dt>Mobile</dt>
            <dd>{{ currentUser.mobile || currentUser.phone || 'Not set' }}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{{ currentUser.email || 'Not set' }}</dd>
          </div>
        </dl>
      </section>

      <p :class="['notice', notice.type]">{{ notice.text }}</p>
    </section>

    <section v-if="programSheetOpen" class="sheet-backdrop" @click.self="closeProgramSheet">
      <div class="program-sheet" role="dialog" aria-modal="true" aria-label="Program list">
        <div class="sheet-header">
          <div>
            <p class="eyebrow">PROGRAM LIST</p>
            <h2>{{ activeFilter.label }}</h2>
          </div>
          <button class="ghost-button" type="button" @click="closeProgramSheet">
            Close
          </button>
        </div>

        <div v-if="programLoading" class="empty-state sheet-state">
          <strong>Loading programs</strong>
          <p>Fetching the matching program page.</p>
        </div>

        <div v-else-if="programs.length === 0" class="empty-state sheet-state">
          <strong>No programs</strong>
          <p>There is no data for the current filter.</p>
        </div>

        <div v-else class="program-list">
          <button
            v-for="program in programs"
            :key="program.id"
            type="button"
            class="program-item"
            @click="openProgramDetail(program.id)"
          >
            <div class="poster">
              <img v-if="program.itemPicture" :src="program.itemPicture" alt="" />
              <span v-else>{{ program.title.slice(0, 2) }}</span>
            </div>
            <div class="program-content">
              <h3>{{ program.title }}</h3>
              <p>{{ programMeta(program) }}</p>
              <dl>
                <div>
                  <dt>Area</dt>
                  <dd>{{ program.areaId }}</dd>
                </div>
                <div>
                  <dt>Issue</dt>
                  <dd>{{ formatDateTime(program.issueTime) }}</dd>
                </div>
              </dl>
            </div>
          </button>
        </div>

        <div class="pagination">
          <button class="ghost-button" type="button" :disabled="pageNumber <= 1 || programLoading" @click="previousPage">
            Previous
          </button>
          <span>Page {{ pageNumber }}</span>
          <button class="ghost-button" type="button" :disabled="!hasNextPage || programLoading" @click="nextPage">
            Next
          </button>
        </div>
      </div>
    </section>

    <div v-if="detailLoading" class="loading-mask">
      <span>Loading detail...</span>
    </div>
  </main>
</template>
