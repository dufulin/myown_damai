<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ArrowLeft,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  CircleDollarSign,
  House,
  LayoutDashboard,
  LogOut,
  Power,
  ReceiptText,
  RefreshCw,
  Search,
  ShieldCheck,
  TimerReset,
  UserRound,
  UserRoundCog
} from '@lucide/vue'

const apiBase = import.meta.env.VITE_API_BASE || `http://${window.location.hostname}:8080`
const token = ref('')
const currentUser = ref(null)
let refreshPromise = null
const mode = ref('login')
const loading = ref(false)
const programLoading = ref(false)
const homeProgramLoading = ref(false)
const detailLoading = ref(false)
const orderLoading = ref(false)
const orderListLoading = ref(false)
const adminLoading = ref(false)
const adminActionLoading = ref('')
const programCategories = ref([])
const homePrograms = ref([])
const programs = ref([])
const orders = ref([])
const ticketUsers = ref([])
const selectedProgramDetail = ref(null)
const adminDashboard = ref(null)
const adminUsers = ref([])
const adminPrograms = ref([])
const adminOrders = ref([])
const adminTab = ref('overview')
const viewMode = ref('home')
const searchKeyword = ref('')
const pageNumber = ref(1)
const orderPageNumber = ref(1)
const pageSize = 6
const orderPageSize = 8
const homePageSize = 10
const adminPageSize = 12
const programSheetOpen = ref(false)
const notice = reactive({ type: 'info', text: `Backend API: ${apiBase}` })
const activeFilter = reactive({
  label: 'All Programs',
  categoryId: null,
  areaId: null,
  keyword: ''
})

const adminPages = reactive({
  users: 1,
  programs: 1,
  orders: 1
})

const adminTotals = reactive({
  users: 0,
  programs: 0,
  orders: 0
})

const adminFilters = reactive({
  userKeyword: '',
  userRole: '',
  programKeyword: '',
  programStatus: '',
  orderNumber: '',
  orderUserId: '',
  orderProgramId: '',
  orderStatus: ''
})

const adminRoleDrafts = reactive({})

const searchFilters = reactive({
  areaId: '',
  programCategoryId: '',
  timeType: 0,
  startDateTime: '',
  endDateTime: '',
  type: 1
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

const timeOptions = [
  { value: 0, label: '全部时间' },
  { value: 1, label: '今天' },
  { value: 2, label: '明天' },
  { value: 3, label: '一周内' },
  { value: 4, label: '一个月内' },
  { value: 5, label: '按日历' }
]

const sortOptions = [
  { value: 1, label: '相关度排序' },
  { value: 2, label: '推荐排序' },
  { value: 3, label: '最近开场' },
  { value: 4, label: '最新上架' }
]

const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))
const canAccessAdmin = computed(() => ['OPERATOR', 'ADMIN'].includes(currentUser.value?.role))
const isAdministrator = computed(() => currentUser.value?.role === 'ADMIN')
const pageTitle = computed(() => {
  if (!isLoggedIn.value) {
    return 'Damai Ticket Management'
  }
  if (viewMode.value === 'admin') {
    return '运营管理控制台'
  }
  if (viewMode.value === 'profile') {
    return '个人中心'
  }
  if (viewMode.value === 'detail') {
    return '节目详情'
  }
  return 'Program Home'
})
const title = computed(() => (mode.value === 'login' ? 'Login' : 'Register'))
const hasNextPage = computed(() => programs.value.length === pageSize)
const hasNextOrderPage = computed(() => orders.value.length === orderPageSize)
const hasNextAdminUserPage = computed(() => adminPages.users * adminPageSize < adminTotals.users)
const hasNextAdminProgramPage = computed(() => adminPages.programs * adminPageSize < adminTotals.programs)
const hasNextAdminOrderPage = computed(() => adminPages.orders * adminPageSize < adminTotals.orders)
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
const categorySearchOptions = computed(() => {
  const children = programCategories.value.filter((category) => category.id && category.parentId !== 0)
  const source = children.length > 0 ? children : programCategories.value.filter((category) => category.id)
  return [{ id: '', name: '全部类型' }, ...source]
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
 * Keeps the short-lived access token in memory only.
 */
function saveToken(nextToken) {
  token.value = nextToken || ''
}

/**
 * Requests and stores a rotated access token through the HttpOnly refresh cookie.
 */
async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = fetch(`${apiBase}/api/users/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' }
    })
      .then(async (response) => {
        const payload = await response.json().catch(() => null)
        if (!response.ok) {
          throw new Error(payload?.message || 'Session expired. Please log in again.')
        }
        saveToken(payload.data.token)
        currentUser.value = payload.data.user
        return payload
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

/**
 * Sends a JSON request and refreshes an expired access token once.
 */
async function request(path, options = {}) {
  const { skipAuthRefresh = false, ...fetchOptions } = options
  const headers = {
    'Content-Type': 'application/json',
    ...(fetchOptions.headers || {})
  }

  if (token.value) {
    headers.Authorization = `Bearer ${token.value}`
  }

  const response = await fetch(`${apiBase}${path}`, {
    ...fetchOptions,
    credentials: 'include',
    headers
  })
  const payload = await response.json().catch(() => null)

  if (response.status === 401 && !skipAuthRefresh) {
    try {
      await refreshAccessToken()
      return request(path, { ...fetchOptions, skipAuthRefresh: true })
    } catch (error) {
      saveToken('')
      currentUser.value = null
      throw error
    }
  }
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
    0: '待创建',
    1: 'Unpaid',
    2: 'Canceled',
    3: 'Paid',
    4: 'Refunded',
    5: 'Timed out'
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
 * Formats numeric amounts for management summaries and tables.
 */
function formatCurrency(value) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

/**
 * Converts a program business status to compact management text.
 */
function programStatusText(status) {
  return Number(status) === 1 ? '在售' : '已下架'
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
      body: JSON.stringify(body),
      skipAuthRefresh: true
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
 * Restores the session by rotating the server-managed refresh cookie.
 */
async function loadCurrentUser() {
  loading.value = true
  try {
    await refreshAccessToken()
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
    await request('/api/users/logout', {
      method: 'POST',
      skipAuthRefresh: true
    })
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
    adminDashboard.value = null
    adminUsers.value = []
    adminPrograms.value = []
    adminOrders.value = []
    adminTab.value = 'overview'
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
  searchFilters.programCategoryId = category.id ? String(category.id) : ''
  searchFilters.areaId = ''
  searchKeyword.value = activeFilter.keyword
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
  searchFilters.areaId = area.id ? String(area.id) : ''
  searchFilters.programCategoryId = ''
  searchKeyword.value = ''
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
  activeFilter.categoryId = searchFilters.programCategoryId ? Number(searchFilters.programCategoryId) : null
  activeFilter.areaId = searchFilters.areaId ? Number(searchFilters.areaId) : null
  activeFilter.keyword = keyword
  programSheetOpen.value = true
  await loadPrograms()
}

/**
 * Appends one optional query parameter when its value is present.
 */
function appendOptionalParam(params, key, value) {
  if (value !== null && value !== undefined && value !== '') {
    params.set(key, String(value))
  }
}

/**
 * Validates calendar search fields before sending the search request.
 */
function validateCalendarSearch() {
  if (Number(searchFilters.timeType) !== 5) {
    return true
  }
  if (!searchFilters.startDateTime || !searchFilters.endDateTime) {
    setNotice('error', '按日历搜索时，请选择开始和结束日期。')
    return false
  }
  return true
}

/**
 * Loads one page of programs from the backend.
 */
async function loadPrograms() {
  if (!validateCalendarSearch()) {
    return
  }
  programLoading.value = true
  try {
    const params = new URLSearchParams()
    params.set('pageNumber', String(pageNumber.value))
    params.set('pageSize', String(pageSize))
    params.set('timeType', String(searchFilters.timeType))
    params.set('type', String(searchFilters.type))
    appendOptionalParam(params, 'keyword', activeFilter.keyword)
    appendOptionalParam(params, 'programCategoryId', searchFilters.programCategoryId)
    appendOptionalParam(params, 'areaId', searchFilters.areaId)
    if (Number(searchFilters.timeType) === 5) {
      params.set('startDateTime', searchFilters.startDateTime)
      params.set('endDateTime', searchFilters.endDateTime)
    }
    const result = await request(`/api/programs/search?${params.toString()}`)
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
    const body = {
      programId: selectedProgram.value.id,
      showTimeId: Number(orderForm.showTimeId),
      ticketCategoryId: Number(orderForm.ticketCategoryId),
      ticketUserIds: orderForm.ticketUserIds.map(Number)
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
 * Opens the role-protected management console and loads its overview.
 */
async function openAdminCenter() {
  if (!canAccessAdmin.value) {
    setNotice('error', '当前账号没有管理端权限。')
    return
  }
  viewMode.value = 'admin'
  adminTab.value = 'overview'
  programSheetOpen.value = false
  selectedProgramDetail.value = null
  await loadAdminDashboard()
}

/**
 * Selects one management view and loads its current page.
 */
async function selectAdminTab(nextTab) {
  adminTab.value = nextTab
  if (nextTab === 'overview') {
    await loadAdminDashboard()
  } else if (nextTab === 'users') {
    await loadAdminUsers()
  } else if (nextTab === 'programs') {
    await loadAdminPrograms()
  } else if (nextTab === 'orders') {
    await loadAdminOrders()
  }
}

/**
 * Loads management counters and recent orders.
 */
async function loadAdminDashboard() {
  adminLoading.value = true
  try {
    const result = await request('/api/admin/dashboard')
    adminDashboard.value = result.data
    setNotice('success', '管理概览已刷新。')
  } catch (error) {
    adminDashboard.value = null
    setNotice('error', error.message)
  } finally {
    adminLoading.value = false
  }
}

/**
 * Loads one filtered management user page.
 */
async function loadAdminUsers(resetPage = false) {
  if (resetPage) {
    adminPages.users = 1
  }
  adminLoading.value = true
  try {
    const params = new URLSearchParams({
      pageNumber: String(adminPages.users),
      pageSize: String(adminPageSize)
    })
    appendOptionalParam(params, 'keyword', adminFilters.userKeyword.trim())
    appendOptionalParam(params, 'role', adminFilters.userRole)
    const result = await request(`/api/admin/users?${params.toString()}`)
    adminUsers.value = result.data.items || []
    adminTotals.users = Number(result.data.total || 0)
    adminUsers.value.forEach((user) => {
      adminRoleDrafts[user.id] = user.role
    })
  } catch (error) {
    adminUsers.value = []
    adminTotals.users = 0
    setNotice('error', error.message)
  } finally {
    adminLoading.value = false
  }
}

/**
 * Loads one filtered management program page.
 */
async function loadAdminPrograms(resetPage = false) {
  if (resetPage) {
    adminPages.programs = 1
  }
  adminLoading.value = true
  try {
    const params = new URLSearchParams({
      pageNumber: String(adminPages.programs),
      pageSize: String(adminPageSize)
    })
    appendOptionalParam(params, 'keyword', adminFilters.programKeyword.trim())
    appendOptionalParam(params, 'programStatus', adminFilters.programStatus)
    const result = await request(`/api/admin/programs?${params.toString()}`)
    adminPrograms.value = result.data.items || []
    adminTotals.programs = Number(result.data.total || 0)
  } catch (error) {
    adminPrograms.value = []
    adminTotals.programs = 0
    setNotice('error', error.message)
  } finally {
    adminLoading.value = false
  }
}

/**
 * Loads one filtered management order page.
 */
async function loadAdminOrders(resetPage = false) {
  if (resetPage) {
    adminPages.orders = 1
  }
  adminLoading.value = true
  try {
    const params = new URLSearchParams({
      pageNumber: String(adminPages.orders),
      pageSize: String(adminPageSize)
    })
    appendOptionalParam(params, 'orderNumber', adminFilters.orderNumber)
    appendOptionalParam(params, 'userId', adminFilters.orderUserId)
    appendOptionalParam(params, 'programId', adminFilters.orderProgramId)
    appendOptionalParam(params, 'orderStatus', adminFilters.orderStatus)
    const result = await request(`/api/admin/orders?${params.toString()}`)
    adminOrders.value = result.data.items || []
    adminTotals.orders = Number(result.data.total || 0)
  } catch (error) {
    adminOrders.value = []
    adminTotals.orders = 0
    setNotice('error', error.message)
  } finally {
    adminLoading.value = false
  }
}

/**
 * Updates a managed account role and refreshes both users and overview.
 */
async function updateManagedUserRole(user) {
  const nextRole = adminRoleDrafts[user.id]
  if (!isAdministrator.value || !nextRole || nextRole === user.role) {
    return
  }
  adminActionLoading.value = `user-${user.id}`
  try {
    await request(`/api/admin/users/${user.id}/role`, {
      method: 'PUT',
      body: JSON.stringify({ role: nextRole })
    })
    setNotice('success', `用户 ${user.mobile} 已调整为 ${nextRole}。`)
    await Promise.all([loadAdminUsers(), loadAdminDashboard()])
  } catch (error) {
    adminRoleDrafts[user.id] = user.role
    setNotice('error', error.message)
  } finally {
    adminActionLoading.value = ''
  }
}

/**
 * Takes one program offline after an explicit operator confirmation.
 */
async function offlineManagedProgram(program) {
  if (!window.confirm(`确认下架节目“${program.title}”吗？`)) {
    return
  }
  adminActionLoading.value = `program-${program.id}`
  try {
    await request(`/api/admin/programs/${program.id}/offline`, { method: 'POST' })
    setNotice('success', `节目已下架：${program.title}`)
    await Promise.all([loadAdminPrograms(), loadAdminDashboard()])
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    adminActionLoading.value = ''
  }
}

/**
 * Triggers the protected timeout-order cancellation workflow.
 */
async function triggerTimeoutCancellation() {
  if (!window.confirm('确认立即执行一次超时订单取消扫描吗？')) {
    return
  }
  adminActionLoading.value = 'timeout'
  try {
    const result = await request('/api/admin/orders/timeout-cancel', { method: 'POST' })
    const canceledCount = result.data?.result?.canceledCount ?? 0
    setNotice('success', `超时取消执行完成，处理 ${canceledCount} 个订单。`)
    await Promise.all([loadAdminDashboard(), adminTab.value === 'orders' ? loadAdminOrders() : Promise.resolve()])
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    adminActionLoading.value = ''
  }
}

/**
 * Triggers one due payment-event compensation scan.
 */
async function triggerPayCompensation() {
  if (!window.confirm('确认立即执行一次支付事件补偿吗？')) {
    return
  }
  adminActionLoading.value = 'pay'
  try {
    const result = await request('/api/admin/pay/events/compensate', { method: 'POST' })
    const processedCount = result.data?.result?.processedCount ?? 0
    setNotice('success', `支付补偿执行完成，处理 ${processedCount} 条事件。`)
    await loadAdminDashboard()
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    adminActionLoading.value = ''
  }
}

/**
 * Moves one management list page backward.
 */
async function previousAdminPage(type) {
  if (adminPages[type] <= 1) {
    return
  }
  adminPages[type] -= 1
  await loadAdminPage(type)
}

/**
 * Moves one management list page forward.
 */
async function nextAdminPage(type) {
  adminPages[type] += 1
  await loadAdminPage(type)
}

/**
 * Dispatches a page reload to the selected management resource.
 */
async function loadAdminPage(type) {
  if (type === 'users') {
    await loadAdminUsers()
  } else if (type === 'programs') {
    await loadAdminPrograms()
  } else {
    await loadAdminOrders()
  }
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
        orderNumber: order.orderNumber
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
        <h1>{{ pageTitle }}</h1>
      </div>
      <div v-if="isLoggedIn" class="topbar-actions">
        <button v-if="viewMode !== 'home'" class="ghost-button icon-text" type="button" @click="initializeHome">
          <House :size="16" aria-hidden="true" />
          首页
        </button>
        <button
          v-if="canAccessAdmin && viewMode !== 'admin'"
          class="ghost-button icon-text"
          type="button"
          :disabled="adminLoading"
          @click="openAdminCenter"
        >
          <LayoutDashboard :size="16" aria-hidden="true" />
          管理控制台
        </button>
        <button class="ghost-button icon-text" type="button" :disabled="orderListLoading" @click="openProfileCenter">
          <UserRound :size="16" aria-hidden="true" />
          个人中心
        </button>
        <button class="ghost-button icon-text" :disabled="loading" @click="logout">
          <LogOut :size="16" aria-hidden="true" />
          退出
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

    <section v-else-if="viewMode === 'admin'" class="admin-layout">
      <div class="detail-toolbar">
        <button class="ghost-button icon-text" type="button" @click="initializeHome">
          <ArrowLeft :size="16" aria-hidden="true" />
          返回首页
        </button>
        <p :class="['notice', notice.type]">{{ notice.text }}</p>
      </div>

      <div class="admin-workspace">
        <aside class="admin-sidebar">
          <div class="admin-identity">
            <span class="admin-avatar"><ShieldCheck :size="22" aria-hidden="true" /></span>
            <div>
              <strong>{{ currentUser.name || currentUser.username || currentUser.mobile }}</strong>
              <span>{{ currentUser.role === 'ADMIN' ? '系统管理员' : '运营人员' }}</span>
            </div>
          </div>

          <nav class="admin-nav" aria-label="管理功能">
            <button type="button" :class="{ active: adminTab === 'overview' }" @click="selectAdminTab('overview')">
              <LayoutDashboard :size="17" aria-hidden="true" />
              经营概览
            </button>
            <button type="button" :class="{ active: adminTab === 'users' }" @click="selectAdminTab('users')">
              <UserRoundCog :size="17" aria-hidden="true" />
              用户管理
            </button>
            <button type="button" :class="{ active: adminTab === 'programs' }" @click="selectAdminTab('programs')">
              <CalendarDays :size="17" aria-hidden="true" />
              节目管理
            </button>
            <button type="button" :class="{ active: adminTab === 'orders' }" @click="selectAdminTab('orders')">
              <ReceiptText :size="17" aria-hidden="true" />
              订单管理
            </button>
          </nav>
        </aside>

        <section class="admin-content">
          <header class="admin-header">
            <div>
              <p class="eyebrow">MANAGEMENT</p>
              <h2 v-if="adminTab === 'overview'">经营概览</h2>
              <h2 v-else-if="adminTab === 'users'">用户管理</h2>
              <h2 v-else-if="adminTab === 'programs'">节目管理</h2>
              <h2 v-else>订单管理</h2>
            </div>
            <div class="admin-header-actions">
              <button
                v-if="adminTab === 'overview'"
                class="ghost-button icon-text"
                type="button"
                :disabled="Boolean(adminActionLoading)"
                @click="triggerTimeoutCancellation"
              >
                <TimerReset :size="16" aria-hidden="true" />
                执行超时取消
              </button>
              <button
                v-if="adminTab === 'overview'"
                class="ghost-button icon-text"
                type="button"
                :disabled="Boolean(adminActionLoading)"
                @click="triggerPayCompensation"
              >
                <CircleDollarSign :size="16" aria-hidden="true" />
                补偿支付事件
              </button>
              <button class="ghost-button icon-text" type="button" :disabled="adminLoading" @click="selectAdminTab(adminTab)">
                <RefreshCw :size="16" :class="{ spinning: adminLoading }" aria-hidden="true" />
                刷新
              </button>
            </div>
          </header>

          <div v-if="adminLoading" class="admin-loading">
            <RefreshCw :size="22" class="spinning" aria-hidden="true" />
            <span>正在加载管理数据</span>
          </div>

          <template v-else-if="adminTab === 'overview'">
            <div v-if="adminDashboard" class="metric-grid">
              <article class="metric-tile">
                <span>注册用户</span>
                <strong>{{ adminDashboard.totalUsers }}</strong>
                <small>累计账号数</small>
              </article>
              <article class="metric-tile">
                <span>在售节目</span>
                <strong>{{ adminDashboard.activePrograms }}</strong>
                <small>当前可售节目</small>
              </article>
              <article class="metric-tile">
                <span>待支付订单</span>
                <strong>{{ adminDashboard.pendingPaymentOrders }}</strong>
                <small>等待用户支付</small>
              </article>
              <article class="metric-tile">
                <span>已支付订单</span>
                <strong>{{ adminDashboard.paidOrders }}</strong>
                <small>支付完成订单</small>
              </article>
              <article class="metric-tile">
                <span>超时订单</span>
                <strong>{{ adminDashboard.timeoutOrders }}</strong>
                <small>已自动释放库存</small>
              </article>
              <article class="metric-tile">
                <span>支付金额</span>
                <strong class="metric-money">¥{{ formatCurrency(adminDashboard.paidAmount) }}</strong>
                <small>已支付订单总额</small>
              </article>
            </div>

            <section class="admin-panel">
              <div class="admin-panel-heading">
                <div>
                  <p class="eyebrow">RECENT ORDERS</p>
                  <h3>最新订单</h3>
                </div>
                <button class="text-action" type="button" @click="selectAdminTab('orders')">查看全部</button>
              </div>
              <div v-if="!adminDashboard?.recentOrders?.length" class="admin-empty">暂无订单数据</div>
              <div v-else class="admin-table-wrap">
                <table class="admin-table order-table">
                  <thead>
                    <tr>
                      <th>订单号</th>
                      <th>节目</th>
                      <th>用户 / 节目 ID</th>
                      <th>金额</th>
                      <th>状态</th>
                      <th>创建时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="order in adminDashboard.recentOrders" :key="order.id">
                      <td class="mono-cell">{{ order.orderNumber }}</td>
                      <td><strong class="table-primary">{{ order.programTitle || '节目快照缺失' }}</strong></td>
                      <td>{{ order.userId }} / {{ order.programId }}</td>
                      <td>¥{{ formatCurrency(order.orderPrice) }}</td>
                      <td><span :class="orderStatusClass(order.orderStatus)">{{ orderStatusText(order.orderStatus) }}</span></td>
                      <td>{{ formatDateTime(order.createdAt) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </template>

          <template v-else-if="adminTab === 'users'">
            <form class="admin-filters user-filters" @submit.prevent="loadAdminUsers(true)">
              <label>
                <span>账号关键词</span>
                <input v-model.trim="adminFilters.userKeyword" placeholder="姓名、手机号或邮箱" maxlength="100" />
              </label>
              <label>
                <span>角色</span>
                <select v-model="adminFilters.userRole">
                  <option value="">全部角色</option>
                  <option value="USER">普通用户</option>
                  <option value="OPERATOR">运营人员</option>
                  <option value="ADMIN">系统管理员</option>
                </select>
              </label>
              <button class="primary-button icon-text" type="submit">
                <Search :size="16" aria-hidden="true" />
                查询
              </button>
            </form>

            <div class="admin-result-bar">
              <span>共 {{ adminTotals.users }} 个用户</span>
              <small v-if="!isAdministrator">运营人员仅可查看账号角色</small>
            </div>
            <div v-if="adminUsers.length === 0" class="admin-empty">没有符合条件的用户</div>
            <div v-else class="admin-table-wrap">
              <table class="admin-table user-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>用户</th>
                    <th>联系方式</th>
                    <th>角色</th>
                    <th>注册时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="user in adminUsers" :key="user.id">
                    <td class="mono-cell">{{ user.id }}</td>
                    <td><strong class="table-primary">{{ user.name || '未设置姓名' }}</strong></td>
                    <td>
                      <span class="table-stack">{{ user.mobile || '未设置手机' }}<small>{{ user.email || '未设置邮箱' }}</small></span>
                    </td>
                    <td>
                      <select v-if="isAdministrator" v-model="adminRoleDrafts[user.id]" class="compact-select">
                        <option value="USER">普通用户</option>
                        <option value="OPERATOR">运营人员</option>
                        <option value="ADMIN">系统管理员</option>
                      </select>
                      <span v-else class="role-pill">{{ user.role }}</span>
                    </td>
                    <td>{{ formatDateTime(user.createdAt) }}</td>
                    <td>
                      <button
                        v-if="isAdministrator"
                        class="table-action"
                        type="button"
                        :disabled="adminRoleDrafts[user.id] === user.role || adminActionLoading === `user-${user.id}`"
                        @click="updateManagedUserRole(user)"
                      >
                        保存角色
                      </button>
                      <span v-else class="muted">只读</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="admin-pagination">
              <button class="icon-button" type="button" title="上一页" aria-label="上一页" :disabled="adminPages.users <= 1" @click="previousAdminPage('users')">
                <ChevronLeft :size="18" aria-hidden="true" />
              </button>
              <span>第 {{ adminPages.users }} 页</span>
              <button class="icon-button" type="button" title="下一页" aria-label="下一页" :disabled="!hasNextAdminUserPage" @click="nextAdminPage('users')">
                <ChevronRight :size="18" aria-hidden="true" />
              </button>
            </div>
          </template>

          <template v-else-if="adminTab === 'programs'">
            <form class="admin-filters program-filters" @submit.prevent="loadAdminPrograms(true)">
              <label>
                <span>节目关键词</span>
                <input v-model.trim="adminFilters.programKeyword" placeholder="节目标题" maxlength="100" />
              </label>
              <label>
                <span>售卖状态</span>
                <select v-model="adminFilters.programStatus">
                  <option value="">全部状态</option>
                  <option value="1">在售</option>
                  <option value="0">已下架</option>
                </select>
              </label>
              <button class="primary-button icon-text" type="submit">
                <Search :size="16" aria-hidden="true" />
                查询
              </button>
            </form>

            <div class="admin-result-bar"><span>共 {{ adminTotals.programs }} 个节目</span></div>
            <div v-if="adminPrograms.length === 0" class="admin-empty">没有符合条件的节目</div>
            <div v-else class="admin-table-wrap">
              <table class="admin-table program-admin-table">
                <thead>
                  <tr>
                    <th>节目</th>
                    <th>地区 / 分类 ID</th>
                    <th>票档库存</th>
                    <th>状态</th>
                    <th>上架时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="program in adminPrograms" :key="program.id">
                    <td>
                      <strong class="table-primary">{{ program.title }}</strong>
                      <small class="table-secondary">ID {{ program.id }}</small>
                    </td>
                    <td>{{ program.areaId }} / {{ program.programCategoryId }}</td>
                    <td><span class="stock-value">{{ program.remainingStock }} / {{ program.totalStock }}</span></td>
                    <td>
                      <span :class="['program-status', Number(program.programStatus) === 1 ? 'on-sale' : 'offline']">
                        {{ programStatusText(program.programStatus) }}
                      </span>
                    </td>
                    <td>{{ formatDateTime(program.issueTime) }}</td>
                    <td>
                      <button
                        class="table-action danger"
                        type="button"
                        :disabled="Number(program.programStatus) !== 1 || adminActionLoading === `program-${program.id}`"
                        @click="offlineManagedProgram(program)"
                      >
                        <Power :size="15" aria-hidden="true" />
                        下架
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="admin-pagination">
              <button class="icon-button" type="button" title="上一页" aria-label="上一页" :disabled="adminPages.programs <= 1" @click="previousAdminPage('programs')">
                <ChevronLeft :size="18" aria-hidden="true" />
              </button>
              <span>第 {{ adminPages.programs }} 页</span>
              <button class="icon-button" type="button" title="下一页" aria-label="下一页" :disabled="!hasNextAdminProgramPage" @click="nextAdminPage('programs')">
                <ChevronRight :size="18" aria-hidden="true" />
              </button>
            </div>
          </template>

          <template v-else>
            <form class="admin-filters order-filters" @submit.prevent="loadAdminOrders(true)">
              <label>
                <span>订单号</span>
                <input v-model.trim="adminFilters.orderNumber" inputmode="numeric" placeholder="完整订单号" />
              </label>
              <label>
                <span>用户 ID</span>
                <input v-model.trim="adminFilters.orderUserId" inputmode="numeric" placeholder="用户 ID" />
              </label>
              <label>
                <span>节目 ID</span>
                <input v-model.trim="adminFilters.orderProgramId" inputmode="numeric" placeholder="节目 ID" />
              </label>
              <label>
                <span>订单状态</span>
                <select v-model="adminFilters.orderStatus">
                  <option value="">全部状态</option>
                  <option value="0">待创建</option>
                  <option value="1">待支付</option>
                  <option value="2">已取消</option>
                  <option value="3">已支付</option>
                  <option value="4">已退款</option>
                  <option value="5">已超时</option>
                </select>
              </label>
              <button class="primary-button icon-text" type="submit">
                <Search :size="16" aria-hidden="true" />
                查询
              </button>
            </form>

            <div class="admin-result-bar"><span>共 {{ adminTotals.orders }} 个订单</span></div>
            <div v-if="adminOrders.length === 0" class="admin-empty">没有符合条件的订单</div>
            <div v-else class="admin-table-wrap">
              <table class="admin-table order-table">
                <thead>
                  <tr>
                    <th>订单号</th>
                    <th>节目</th>
                    <th>用户 / 节目 ID</th>
                    <th>金额</th>
                    <th>状态</th>
                    <th>创建 / 过期时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="order in adminOrders" :key="order.id">
                    <td class="mono-cell">{{ order.orderNumber }}</td>
                    <td><strong class="table-primary">{{ order.programTitle || '节目快照缺失' }}</strong></td>
                    <td>{{ order.userId }} / {{ order.programId }}</td>
                    <td>¥{{ formatCurrency(order.orderPrice) }}</td>
                    <td><span :class="orderStatusClass(order.orderStatus)">{{ orderStatusText(order.orderStatus) }}</span></td>
                    <td>
                      <span class="table-stack">{{ formatDateTime(order.createdAt) }}<small>{{ formatDateTime(order.expireTime) }}</small></span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="admin-pagination">
              <button class="icon-button" type="button" title="上一页" aria-label="上一页" :disabled="adminPages.orders <= 1" @click="previousAdminPage('orders')">
                <ChevronLeft :size="18" aria-hidden="true" />
              </button>
              <span>第 {{ adminPages.orders }} 页</span>
              <button class="icon-button" type="button" title="下一页" aria-label="下一页" :disabled="!hasNextAdminOrderPage" @click="nextAdminPage('orders')">
                <ChevronRight :size="18" aria-hidden="true" />
              </button>
            </div>
          </template>
        </section>
      </div>
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
          <label class="search-field wide">
            <span>关键词</span>
            <input v-model.trim="searchKeyword" placeholder="节目名、艺人、场馆" maxlength="80" />
          </label>
          <label class="search-field">
            <span>城市</span>
            <select v-model="searchFilters.areaId">
              <option v-for="area in areaOptions" :key="`search-area-${area.id || area.label}`" :value="area.id ? String(area.id) : ''">
                {{ area.label === 'All Areas' ? '全部城市' : area.label }}
              </option>
            </select>
          </label>
          <label class="search-field">
            <span>节目类型</span>
            <select v-model="searchFilters.programCategoryId">
              <option v-for="category in categorySearchOptions" :key="`search-category-${category.id || category.name}`" :value="category.id ? String(category.id) : ''">
                {{ category.name }}
              </option>
            </select>
          </label>
          <label class="search-field">
            <span>时间</span>
            <select v-model.number="searchFilters.timeType">
              <option v-for="item in timeOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
          <label v-if="Number(searchFilters.timeType) === 5" class="search-field">
            <span>开始日期</span>
            <input v-model="searchFilters.startDateTime" type="date" />
          </label>
          <label v-if="Number(searchFilters.timeType) === 5" class="search-field">
            <span>结束日期</span>
            <input v-model="searchFilters.endDateTime" type="date" />
          </label>
          <label class="search-field">
            <span>排序</span>
            <select v-model.number="searchFilters.type">
              <option v-for="item in sortOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
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
