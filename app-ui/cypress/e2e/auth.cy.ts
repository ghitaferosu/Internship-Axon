describe('Authentication flow', () => {
  const email = `test-${Date.now()}@test.com`
  const password = 'parola1234'

  it('inregistrare, autentificare, deconectare', () => {
    cy.visit('/register')

    cy.get('#register-firstName').should('be.visible').type('Test')
    cy.get('#register-lastName').type('Utilizator')
    cy.get('#register-email').type(email)
    cy.get('#register-password').type(password)
    cy.get('button[type="submit"]').click()

    cy.url().should('include', '/login')

    cy.get('#login-email').type(email)
    cy.get('#login-password').type(password)
    cy.get('button[type="submit"]').click()

    cy.url().should('include', '/home')

    cy.window().its('sessionStorage').invoke('getItem', 'accessToken').should('not.be.null')
    cy.window().its('sessionStorage').invoke('getItem', 'refreshToken').should('not.be.null')

    cy.get('button').click()

    cy.url().should('include', '/login')

    cy.window().its('sessionStorage').invoke('getItem', 'accessToken').should('be.null')
  })
})


