// Reads claims out of the access token issued by user-service's JwtService
// (uid/roles/type, see JwtService.generateToken). Decode-only, no signature
// check - the backend already verified it; the frontend needs `uid` only to
// recognise its own seats (see EventBookingPage's mineStatus/getMyTickets),
// never to identify itself to the API - the gateway reads that off the JWT.
interface AccessTokenClaims {
    uid: number
    roles: string[]
    type: 'ACCESS' | 'REFRESH'
}

function base64UrlDecode(segment: string): string {
    const padded = segment.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(segment.length / 4) * 4, '=')
    return atob(padded)
}

export function decodeAccessToken(token: string): AccessTokenClaims | null {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    try {
        return JSON.parse(base64UrlDecode(parts[1])) as AccessTokenClaims
    } catch {
        return null
    }
}

export function getUserId(token: string): number | null {
    return decodeAccessToken(token)?.uid ?? null
}

export function getRoles(token: string): string[] {
    return decodeAccessToken(token)?.roles ?? []
}
