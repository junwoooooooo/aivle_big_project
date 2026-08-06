const NO_AUTH_RETRY = {
  authenticate: false,
  refreshOnUnauthorized: false,
};

export function createAuthApi(client) {
  return {
    async signup(input) {
      const response = await client.post('/auth/signup', input, NO_AUTH_RETRY);
      return response.data;
    },
    async login(input) {
      const response = await client.post('/auth/login', input, NO_AUTH_RETRY);
      return response.data;
    },
    async refresh(refreshToken) {
      const response = await client.post(
        '/auth/refresh',
        { refreshToken },
        NO_AUTH_RETRY,
      );
      return response.data;
    },
    async logout(refreshToken) {
      await client.post(
        '/auth/logout',
        { refreshToken },
        { refreshOnUnauthorized: false },
      );
    },
    async getMe() {
      const response = await client.get('/users/me');
      return response.data;
    },
    async updateProfile(input) {
      const response = await client.patch('/users/me', input);
      return response.data;
    },
    async changePassword(input) {
      await client.post('/users/me/password', input);
    },
    async deleteAccount(input) {
      await client.delete('/users/me', { body: input, refreshOnUnauthorized: false });
    },
  };
}
