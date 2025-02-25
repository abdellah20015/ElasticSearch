<template>
  <div class="min-h-screen bg-gray-100">
    <nav class="bg-white shadow-md p-4">
      <div class="max-w-7xl mx-auto flex justify-between items-center">
        <h1 class="text-xl font-medium text-gray-800">Data Manager</h1>
        <div class="space-x-4">
          <router-link to="/" class="text-blue-600 hover:text-blue-800">Upload</router-link>
          <router-link to="/search" class="text-blue-600 hover:text-blue-800">Search</router-link>
        </div>
      </div>
    </nav>
    <main class="max-w-7xl mx-auto py-6 px-4">
      <router-view />
    </main>
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>
