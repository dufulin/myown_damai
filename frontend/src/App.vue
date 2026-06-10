<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const tokenKey = 'damai_user_token'
const apiBase = import.meta.env.VITE_API_BASE || 'http://127.0.0.1:8080'
const token = ref(localStorage.getItem(tokenKey) || '')
const currentUser = ref(null)
const mode = ref('login')
const loading = ref(false)
const notice = reactive({ type: 'info', text: `Backend API: ${apiBase}` })

const form = reactive({
  login: '',
  password: '',
  name: '',
  mobile: '',
  email: ''
})

const isLoggedIn = computed(() => Boolean(token.value && currentUser.value))
const title = computed(() => (mode.value === 'login' ? 'Login' : 'Register'))

function setNotice(type, text) {
  notice.type = type
  notice.text = text
}

function saveToken(nextToken) {
  token.value = nextToken || ''
  if (nextToken) {
    localStorage.setItem(tokenKey, nextToken)
    return
  }
  localStorage.removeItem(tokenKey)
}

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
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    loading.value = false
  }
}

async function loadCurrentUser() {
  if (!token.value) {
    return
  }
  loading.value = true
  try {
    const result = await request('/api/users/me')
    currentUser.value = result.data
    setNotice('success', 'Session restored.')
  } catch (error) {
    saveToken('')
    currentUser.value = null
    setNotice('error', error.message)
  } finally {
    loading.value = false
  }
}

async function logout() {
  loading.value = true
  try {
    // Revoke the backend token before clearing the browser state.
    await request('/api/users/logout', { method: 'POST' })
    setNotice('success', 'Logged out.')
  } catch (error) {
    setNotice('error', error.message)
  } finally {
    saveToken('')
    currentUser.value = null
    loading.value = false
  }
}

function switchMode(nextMode) {
  mode.value = nextMode
  setNotice('info', nextMode === 'login' ? 'Enter mobile/email credentials.' : 'Create a new account.')
}

onMounted(loadCurrentUser)
</script>

<template>
  <main class="shell">
    <section class="topbar">
      <div>
        <p class="eyebrow">DAMAI TICKET OPS</p>
        <h1>Damai Ticket Management</h1>
      </div>
      <button v-if="isLoggedIn" class="ghost-button" :disabled="loading" @click="logout">
        Logout
      </button>
    </section>

    <section class="workspace">
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
          <span :class="['status-dot', isLoggedIn ? 'online' : 'offline']"></span>
        </div>

        <div v-if="isLoggedIn" class="profile">
          <strong>{{ currentUser.name || currentUser.username || currentUser.mobile }}</strong>
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
        </div>

        <div v-else class="empty-state">
          <strong>Not logged in</strong>
          <p>Log in to view the current user and verify logout behavior.</p>
        </div>

        <p :class="['notice', notice.type]">{{ notice.text }}</p>
      </aside>
    </section>
  </main>
</template>
